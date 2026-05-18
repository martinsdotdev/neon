import { CameraView } from "expo-camera"
import {
  Modal,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native"
import type { ScannerState } from "./useScanner"

interface Props {
  scanner: ScannerState
  /** Optional hint shown above the viewfinder, e.g. "Scan PICK-A01" */
  hint?: string
}

/** Full-screen modal that opens the device camera and reports barcode
  * scans through the supplied `scanner.onCameraBarcode` callback. Render
  * once near the screen root and toggle visibility via
  * `scanner.cameraOpen`.
  */
export const ScannerOverlay = ({ scanner, hint }: Props) => (
  <Modal
    animationType="slide"
    onRequestClose={scanner.closeCamera}
    presentationStyle="fullScreen"
    visible={scanner.cameraOpen}
  >
    <View style={styles.fullscreen}>
      {scanner.permissionGranted ? (
        <CameraView
          barcodeScannerSettings={{ barcodeTypes: [...scanner.barcodeTypes] }}
          facing="back"
          onBarcodeScanned={scanner.onCameraBarcode}
          style={styles.camera}
        />
      ) : (
        <View style={styles.permissionGate}>
          <Text style={styles.permissionTitle}>Camera permission needed</Text>
          <Text style={styles.permissionBody}>
            Neon WES needs camera access to scan barcodes on locations,
            SKUs, and handling units.
          </Text>
          <TouchableOpacity
            onPress={scanner.requestPermission}
            style={styles.permissionButton}
          >
            <Text style={styles.permissionButtonText}>Grant access</Text>
          </TouchableOpacity>
        </View>
      )}

      <View style={styles.overlay} pointerEvents="box-none">
        <View style={styles.topBar}>
          {hint ? <Text style={styles.hint}>{hint}</Text> : null}
          <TouchableOpacity
            accessibilityRole="button"
            onPress={scanner.closeCamera}
            style={styles.closeButton}
          >
            <Text style={styles.closeText}>Close</Text>
          </TouchableOpacity>
        </View>
        <View style={styles.viewfinder} pointerEvents="none" />
      </View>
    </View>
  </Modal>
)

const styles = StyleSheet.create({
  camera: {
    flex: 1,
  },
  closeButton: {
    backgroundColor: "rgba(0,0,0,0.6)",
    borderRadius: 999,
    paddingHorizontal: 16,
    paddingVertical: 8,
  },
  closeText: {
    color: "white",
    fontWeight: "600",
  },
  fullscreen: {
    backgroundColor: "black",
    flex: 1,
  },
  hint: {
    backgroundColor: "rgba(0,0,0,0.6)",
    borderRadius: 8,
    color: "white",
    fontSize: 16,
    fontWeight: "600",
    overflow: "hidden",
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  overlay: {
    bottom: 0,
    flex: 1,
    left: 0,
    position: "absolute",
    right: 0,
    top: 0,
  },
  permissionBody: {
    color: "white",
    fontSize: 14,
    marginBottom: 24,
    opacity: 0.8,
    textAlign: "center",
  },
  permissionButton: {
    backgroundColor: "#16a34a",
    borderRadius: 8,
    paddingHorizontal: 24,
    paddingVertical: 12,
  },
  permissionButtonText: {
    color: "white",
    fontSize: 16,
    fontWeight: "600",
  },
  permissionGate: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center",
    paddingHorizontal: 32,
  },
  permissionTitle: {
    color: "white",
    fontSize: 20,
    fontWeight: "700",
    marginBottom: 12,
  },
  topBar: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingTop: 60,
  },
  viewfinder: {
    alignSelf: "center",
    borderColor: "rgba(22, 163, 74, 0.9)",
    borderRadius: 12,
    borderWidth: 3,
    height: 200,
    marginTop: "40%",
    width: "70%",
  },
})
