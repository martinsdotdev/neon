import { useQuery } from "@tanstack/react-query"
import { Link, router } from "expo-router"
import { useEffect } from "react"
import {
  ActivityIndicator,
  FlatList,
  RefreshControl,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from "react-native"
import { type MobileTask, taskQueries } from "@/src/api/tasks"
import { useAuth } from "@/src/auth/AuthProvider"
import { useNotifications } from "@/src/notifications/useNotifications"

export default function TasksScreen() {
  const { user, status, logout } = useAuth()

  useEffect(() => {
    if (status === "unauthenticated") router.replace("/login")
  }, [status])

  useNotifications(status === "authenticated")

  const query = useQuery({
    ...taskQueries.assigned(user?.userId ?? "", "Assigned"),
    enabled: status === "authenticated" && user !== null,
  })

  if (status !== "authenticated" || !user) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator />
      </View>
    )
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <View style={styles.headerText}>
          <Text style={styles.greeting}>Hi, {user.name}</Text>
          <Text style={styles.role}>{user.role}</Text>
        </View>
        <TouchableOpacity onPress={logout}>
          <Text style={styles.signOut}>Sign out</Text>
        </TouchableOpacity>
      </View>
      <FlatList
        ListEmptyComponent={
          query.isLoading ? (
            <View style={styles.centered}>
              <ActivityIndicator />
            </View>
          ) : (
            <View style={styles.centered}>
              <Text style={styles.empty}>
                {query.isError
                  ? "Couldn't load tasks — pull down to retry."
                  : "No tasks assigned right now."}
              </Text>
            </View>
          )
        }
        data={query.data ?? []}
        keyExtractor={(item) => item.id}
        refreshControl={
          <RefreshControl
            onRefresh={query.refetch}
            refreshing={query.isFetching}
          />
        }
        renderItem={({ item }) => <TaskRow task={item} />}
      />
    </View>
  )
}

const TaskRow = ({ task }: { task: MobileTask }) => (
  <Link
    asChild
    href={{ params: { id: task.id }, pathname: "/tasks/[id]" }}
  >
    <TouchableOpacity style={styles.row}>
      <View style={styles.rowMain}>
        <Text style={styles.rowTitle}>{task.taskType}</Text>
        <Text style={styles.rowSubtitle}>
          SKU {shortId(task.skuId)} · qty {task.requestedQuantity}
        </Text>
      </View>
      <View style={styles.rowMeta}>
        <Text style={styles.rowLocation}>
          {task.sourceLocationId ? shortId(task.sourceLocationId) : "—"}
        </Text>
        <Text style={styles.rowStateBadge}>{task.state}</Text>
      </View>
    </TouchableOpacity>
  </Link>
)

const shortId = (id: string): string => id.slice(0, 8)

const styles = StyleSheet.create({
  centered: {
    alignItems: "center",
    flex: 1,
    justifyContent: "center",
    paddingVertical: 40,
  },
  container: {
    backgroundColor: "#f9fafb",
    flex: 1,
  },
  empty: {
    color: "#6b7280",
    fontSize: 15,
  },
  greeting: {
    fontSize: 18,
    fontWeight: "600",
  },
  header: {
    alignItems: "center",
    borderBottomColor: "#e5e7eb",
    borderBottomWidth: 1,
    flexDirection: "row",
    justifyContent: "space-between",
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  headerText: {
    flexDirection: "column",
  },
  role: {
    color: "#6b7280",
    fontSize: 13,
  },
  row: {
    alignItems: "center",
    backgroundColor: "white",
    borderBottomColor: "#e5e7eb",
    borderBottomWidth: 1,
    flexDirection: "row",
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  rowLocation: {
    color: "#374151",
    fontSize: 13,
    fontVariant: ["tabular-nums"],
  },
  rowMain: {
    flex: 1,
  },
  rowMeta: {
    alignItems: "flex-end",
    gap: 4,
  },
  rowStateBadge: {
    color: "#16a34a",
    fontSize: 12,
    fontWeight: "600",
  },
  rowSubtitle: {
    color: "#6b7280",
    fontSize: 13,
    marginTop: 2,
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: "500",
  },
  signOut: {
    color: "#ef4444",
    fontSize: 14,
    fontWeight: "500",
  },
})
