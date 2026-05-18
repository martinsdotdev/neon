import type { ApiError } from "@neon/domain/error"
import { useQuery } from "@tanstack/react-query"
import { router, useLocalSearchParams } from "expo-router"
import { useState } from "react"
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
import { taskQueries, useCompleteTask } from "@/src/api/tasks"

export default function TaskDetailScreen() {
  const { id } = useLocalSearchParams<{ id: string }>()
  const query = useQuery(taskQueries.byId(id))
  const complete = useCompleteTask(id)

  const [quantity, setQuantity] = useState<string>("")

  if (query.isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator />
      </View>
    )
  }

  if (query.isError || !query.data) {
    return (
      <View style={styles.centered}>
        <Text style={styles.error}>Task not found.</Text>
      </View>
    )
  }

  const task = query.data
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

      <Field label="SKU" value={task.skuId} />
      <Field label="Order" value={task.orderId} />
      <Field
        label="Requested quantity"
        value={task.requestedQuantity.toString()}
      />
      <Field
        label="Source"
        value={task.sourceLocationId ?? "—"}
      />
      <Field
        label="Destination"
        value={task.destinationLocationId ?? "—"}
      />
      {task.handlingUnitId && (
        <Field label="Handling unit" value={task.handlingUnitId} />
      )}

      {task.state === "Assigned" && (
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
    </ScrollView>
  )
}

const Field = ({ label, value }: { label: string; value: string }) => (
  <View style={styles.field}>
    <Text style={styles.fieldLabel}>{label}</Text>
    <Text style={styles.fieldValue}>{value}</Text>
  </View>
)

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
    justifyContent: "space-between",
    paddingVertical: 6,
  },
  fieldLabel: {
    color: "#6b7280",
    fontSize: 14,
  },
  fieldValue: {
    color: "#111827",
    fontSize: 14,
    fontVariant: ["tabular-nums"],
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
