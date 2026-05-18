import type { ApiError } from "@neon/domain/error"
import { useQuery } from "@tanstack/react-query"
import { router, useLocalSearchParams } from "expo-router"
import { useCallback, useState } from "react"
import {
  ActivityIndicator,
  Alert,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native"
import { locationQueries } from "@/src/api/locations"
import { skuQueries } from "@/src/api/skus"
import { taskQueries, useCompleteTask } from "@/src/api/tasks"
import { ScannerOverlay } from "@/src/scanner/ScannerOverlay"
import { type BarcodeScanned, useScanner } from "@/src/scanner/useScanner"

export default function TaskDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const query = useQuery(taskQueries.byId(id))
  const complete = useCompleteTask(id)

  const task = query.data
  const sku = useQuery({
    ...skuQueries.byId(task?.skuId ?? ""),
    enabled: Boolean(task?.skuId),
  })
  const source = useQuery({
    ...locationQueries.byId(task?.sourceLocationId ?? ""),
    enabled: Boolean(task?.sourceLocationId),
  })
  const destination = useQuery({
    ...locationQueries.byId(task?.destinationLocationId ?? ""),
    enabled: Boolean(task?.destinationLocationId),
  })

  const [quantity, setQuantity] = useState<string>("")
  const [sourceVerified, setSourceVerified] = useState(false)
  const [skuVerified, setSkuVerified] = useState(false)
  const [lastScan, setLastScan] = useState<string | null>(null)

  const onScan = useCallback(
    (event: BarcodeScanned) => {
      setLastScan(event.value)
      // Match against the SKU's barcode (SKU.code) or location code. The
      // first matching slot wins; "destination" is left to a later step.
      if (source.data?.code && event.value === source.data.code) {
        setSourceVerified(true)
        scanner.closeCamera()
        return
      }
      if (sku.data?.code && event.value === sku.data.code) {
        setSkuVerified(true)
        scanner.closeCamera()
        return
      }
      // No-match leaves the modal open so the operator can re-scan.
    },
    // scanner is declared below; safe-by-construction (useCallback runs
    // after declaration on every render).
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [source.data?.code, sku.data?.code],
  )
  const scanner = useScanner(onScan)

  const startScan = async () => {
    if (!scanner.permissionGranted) {
      const granted = await scanner.requestPermission()
      if (!granted) {
        Alert.alert(
          "Camera access required",
          "Open Settings → Apps → Neon WES to grant camera permission.",
        )
        return
      }
    }
    scanner.openCamera()
  }

  if (query.isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator />
      </View>
    )
  }

  if (query.isError || !task) {
    return (
      <View style={styles.centered}>
        <Text style={styles.error}>Task not found.</Text>
      </View>
    )
  }

  const hasQuantity = quantity.length > 0 && !Number.isNaN(Number(quantity))

  const onComplete = async () => {
    if (!hasQuantity || complete.isPending) return
    try {
      await complete.mutateAsync({
        actualQuantity: Number(quantity),
        verified: true,
      })
      router.back()
    } catch (error) {
      const apiError = error as ApiError
      const message =
        apiError.kind === "problem"
          ? (apiError.problem.detail ?? apiError.problem.title)
          : apiError.message
      Alert.alert("Couldn't complete task", message)
    }
  }

  return (
    <ScrollView contentContainerStyle={styles.container}>
      <Text style={styles.taskType}>{task.taskType}</Text>
      <Text style={styles.state}>{task.state}</Text>

      <Field
        label="SKU"
        primary={sku.data?.code ?? shortId(task.skuId)}
        secondary={sku.data?.description}
        verified={skuVerified}
      />
      <Field label="Order" primary={shortId(task.orderId)} />
      <Field
        label="Requested quantity"
        primary={task.requestedQuantity.toString()}
      />
      <Field
        label="Source"
        primary={
          source.data?.code ??
          (task.sourceLocationId ? shortId(task.sourceLocationId) : "—")
        }
        secondary={source.data?.locationType}
        verified={sourceVerified}
      />
      <Field
        label="Destination"
        primary={
          destination.data?.code ??
          (task.destinationLocationId
            ? shortId(task.destinationLocationId)
            : "—")
        }
        secondary={destination.data?.locationType}
      />
      {task.handlingUnitId && (
        <Field label="Handling unit" primary={shortId(task.handlingUnitId)} />
      )}

      {task.state === "Assigned" && !(sourceVerified && skuVerified) ? (
        <TouchableOpacity onPress={startScan} style={styles.scanButton}>
          <Text style={styles.scanButtonText}>
            {!sourceVerified
              ? source.data?.code
                ? `Scan source ${source.data.code}`
                : "Scan source"
              : sku.data?.code
                ? `Scan SKU ${sku.data.code}`
                : "Scan SKU"}
          </Text>
        </TouchableOpacity>
      ) : null}

      {lastScan && !(sourceVerified && skuVerified) ? (
        <Text style={styles.lastScan}>Last scanned: {lastScan}</Text>
      ) : null}

      {task.state === "Assigned" && sourceVerified && skuVerified && (
        <View style={styles.completeSection}>
          <Text style={styles.completeLabel}>Actual picked quantity</Text>
          <TextInput
            keyboardType="number-pad"
            onChangeText={setQuantity}
            placeholder={task.requestedQuantity.toString()}
            style={styles.completeInput}
            value={quantity}
          />
          <TouchableOpacity
            disabled={!hasQuantity || complete.isPending}
            onPress={onComplete}
            style={[
              styles.completeButton,
              (!hasQuantity || complete.isPending) &&
                styles.completeButtonDisabled,
            ]}
          >
            {complete.isPending ? (
              <ActivityIndicator color="white" />
            ) : (
              <Text style={styles.completeButtonText}>Complete task</Text>
            )}
          </TouchableOpacity>
        </View>
      )}

      <ScannerOverlay
        hint={
          sourceVerified
            ? sku.data?.code
              ? `Scan SKU ${sku.data.code}`
              : undefined
            : source.data?.code
              ? `Scan source ${source.data.code}`
              : undefined
        }
        scanner={scanner}
      />
    </ScrollView>
  )
}

const Field = ({
  label,
  primary,
  secondary,
  verified,
}: {
  label: string
  primary: string
  secondary?: string
  verified?: boolean
}) => (
  <View style={styles.field}>
    <Text style={styles.fieldLabel}>{label}</Text>
    <View style={styles.fieldValueColumn}>
      <View style={styles.fieldValueRow}>
        {verified ? <Text style={styles.fieldCheck}>✓</Text> : null}
        <Text style={styles.fieldValue}>{primary}</Text>
      </View>
      {secondary ? (
        <Text style={styles.fieldSecondary}>{secondary}</Text>
      ) : null}
    </View>
  </View>
)

const shortId = (id: string): string => id.slice(0, 8)

const styles = StyleSheet.create({
  centered: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center",
  },
  completeButton: {
    alignItems: "center",
    backgroundColor: "#16a34a",
    borderRadius: 8,
    paddingVertical: 14,
  },
  completeButtonDisabled: {
    opacity: 0.5,
  },
  completeButtonText: {
    color: "white",
    fontSize: 16,
    fontWeight: "600",
  },
  completeInput: {
    backgroundColor: "white",
    borderColor: "#d1d5db",
    borderRadius: 8,
    borderWidth: 1,
    fontSize: 18,
    paddingHorizontal: 12,
    paddingVertical: 12,
  },
  completeLabel: {
    color: "#374151",
    fontSize: 14,
    fontWeight: "500",
  },
  completeSection: {
    backgroundColor: "white",
    borderRadius: 12,
    gap: 12,
    marginTop: 24,
    padding: 16,
  },
  container: {
    backgroundColor: "#f9fafb",
    flexGrow: 1,
    padding: 16,
  },
  error: {
    color: "#ef4444",
  },
  field: {
    flexDirection: "row",
    gap: 12,
    justifyContent: "space-between",
    paddingVertical: 8,
  },
  fieldLabel: {
    color: "#6b7280",
    fontSize: 14,
  },
  fieldSecondary: {
    color: "#6b7280",
    fontSize: 12,
    marginTop: 2,
    textAlign: "right",
  },
  fieldValue: {
    color: "#111827",
    fontSize: 14,
    fontVariant: ["tabular-nums"],
    textAlign: "right",
  },
  fieldValueColumn: {
    alignItems: "flex-end",
    flex: 1,
  },
  fieldValueRow: {
    alignItems: "center",
    flexDirection: "row",
    gap: 6,
  },
  fieldCheck: {
    color: "#16a34a",
    fontSize: 16,
    fontWeight: "700",
  },
  lastScan: {
    color: "#6b7280",
    fontSize: 12,
    marginTop: 8,
    textAlign: "center",
  },
  scanButton: {
    alignItems: "center",
    backgroundColor: "#0f172a",
    borderRadius: 8,
    marginTop: 16,
    paddingVertical: 14,
  },
  scanButtonText: {
    color: "white",
    fontSize: 16,
    fontWeight: "600",
  },
  state: {
    color: "#16a34a",
    fontSize: 14,
    fontWeight: "600",
    marginBottom: 16,
  },
  taskType: {
    fontSize: 28,
    fontWeight: "700",
    marginBottom: 4,
  },
})
