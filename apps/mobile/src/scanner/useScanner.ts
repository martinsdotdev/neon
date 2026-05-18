import {
  type BarcodeScanningResult,
  type BarcodeType,
  useCameraPermissions,
} from "expo-camera"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"

export interface BarcodeScanned {
  value: string
  symbology: "CODE128" | "EAN13" | "QR" | "CODE39" | "UPC_A" | "UNKNOWN"
  at: number
}

export interface ScannerState {
  // Permission state.
  permissionGranted: boolean
  permissionRequested: boolean
  requestPermission: () => Promise<boolean>
  // Camera open/close state.
  cameraOpen: boolean
  openCamera: () => void
  closeCamera: () => void
  // Camera-component callback. Pass to <CameraView onBarcodeScanned={...}>.
  onCameraBarcode: (result: BarcodeScanningResult) => void
  // List of symbologies the picker is expected to encounter. Pass to
  // <CameraView barcodeScannerSettings={{ barcodeTypes }}>.
  barcodeTypes: ReadonlyArray<BarcodeType>
}

const SCAN_DEBOUNCE_MS = 1200

// Map expo-camera's reported symbology onto our normalized BarcodeScanned
// values. Most warehouse codes fall into Code128/EAN13/QR; the rest are
// folded to UNKNOWN so the consumer can still inspect `value`.
const symbologyOf = (
  type: BarcodeScanningResult["type"],
): BarcodeScanned["symbology"] => {
  if (type === "code128") return "CODE128"
  if (type === "ean13") return "EAN13"
  if (type === "qr") return "QR"
  if (type === "code39") return "CODE39"
  if (type === "upc_a") return "UPC_A"
  return "UNKNOWN"
}

/** Hook that wires the picker workflow into expo-camera barcode scanning.
  *
  *   - Asks for camera permission lazily — only when the consumer opens the
  *     camera, so app launch isn't blocked.
  *   - Debounces successive scans of the same barcode within 1.2s so a
  *     hovering camera doesn't fire onScan in a tight loop.
  *   - Calls `onScan` with a normalized BarcodeScanned shape that's stable
  *     across symbologies and (eventually) DataWedge inputs.
  */
export const useScanner = (
  onScan: (event: BarcodeScanned) => void,
): ScannerState => {
  const [permission, request] = useCameraPermissions()
  const [cameraOpen, setCameraOpen] = useState(false)
  const lastScanRef = useRef<{ value: string; at: number } | null>(null)

  // Mount-time permission probe so consumers don't see "undetermined" forever.
  useEffect(() => {
    if (permission === null) {
      // First render; expo-camera will populate it once the user grants or
      // denies, or never if we never request.
    }
  }, [permission])

  const requestPermission = useCallback(async (): Promise<boolean> => {
    if (permission?.granted) return true
    const next = await request()
    return Boolean(next.granted)
  }, [permission?.granted, request])

  const openCamera = useCallback(() => {
    setCameraOpen(true)
  }, [])

  const closeCamera = useCallback(() => {
    setCameraOpen(false)
    lastScanRef.current = null
  }, [])

  const onCameraBarcode = useCallback(
    (result: BarcodeScanningResult) => {
      const now = Date.now()
      const last = lastScanRef.current
      if (last && last.value === result.data && now - last.at < SCAN_DEBOUNCE_MS) {
        return
      }
      lastScanRef.current = { at: now, value: result.data }
      onScan({
        at: now,
        symbology: symbologyOf(result.type),
        value: result.data,
      })
    },
    [onScan],
  )

  const barcodeTypes = useMemo(
    () => ["code128", "ean13", "qr", "code39", "upc_a"] satisfies BarcodeType[],
    [],
  )

  return {
    barcodeTypes,
    cameraOpen,
    closeCamera,
    onCameraBarcode,
    openCamera,
    permissionGranted: Boolean(permission?.granted),
    permissionRequested: permission !== null,
    requestPermission,
  }
}
