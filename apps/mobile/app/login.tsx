import type { ApiError } from "@neon/domain/error"
import { router } from "expo-router"
import { useState } from "react"
import {
  ActivityIndicator,
  Alert,
  StyleSheet,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from "react-native"
import { useAuth } from "@/src/auth/AuthProvider"

export default function LoginScreen() {
  const { login } = useAuth()
  const [identifier, setIdentifier] = useState("")
  const [password, setPassword] = useState("")
  const [submitting, setSubmitting] = useState(false)

  const onSubmit = async () => {
    if (submitting) return
    if (!identifier || !password) {
      Alert.alert("Missing fields", "Enter both a login and a password.")
      return
    }
    setSubmitting(true)
    try {
      await login({ login: identifier, password })
      router.replace("/(tabs)")
    } catch (error) {
      const apiError = error as ApiError
      const message =
        apiError.kind === "problem"
          ? (apiError.problem.detail ?? apiError.problem.title)
          : apiError.message
      Alert.alert("Sign in failed", message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Neon WES</Text>
      <Text style={styles.subtitle}>Sign in to start your shift</Text>
      <TextInput
        autoCapitalize="none"
        autoComplete="username"
        autoCorrect={false}
        onChangeText={setIdentifier}
        placeholder="Login"
        style={styles.input}
        textContentType="username"
        value={identifier}
      />
      <TextInput
        autoCapitalize="none"
        autoComplete="password"
        autoCorrect={false}
        onChangeText={setPassword}
        placeholder="Password"
        secureTextEntry
        style={styles.input}
        textContentType="password"
        value={password}
      />
      <TouchableOpacity
        accessibilityRole="button"
        disabled={submitting}
        onPress={onSubmit}
        style={[styles.button, submitting && styles.buttonDisabled]}
      >
        {submitting ? (
          <ActivityIndicator color="white" />
        ) : (
          <Text style={styles.buttonText}>Sign in</Text>
        )}
      </TouchableOpacity>
    </View>
  )
}

const styles = StyleSheet.create({
  button: {
    alignItems: "center",
    backgroundColor: "#16a34a",
    borderRadius: 8,
    marginTop: 8,
    paddingVertical: 14,
  },
  buttonDisabled: {
    opacity: 0.6,
  },
  buttonText: {
    color: "white",
    fontSize: 16,
    fontWeight: "600",
  },
  container: {
    flex: 1,
    gap: 12,
    justifyContent: "center",
    paddingHorizontal: 24,
  },
  input: {
    backgroundColor: "white",
    borderColor: "#d1d5db",
    borderRadius: 8,
    borderWidth: 1,
    fontSize: 16,
    paddingHorizontal: 12,
    paddingVertical: 12,
  },
  subtitle: {
    color: "#6b7280",
    fontSize: 16,
    marginBottom: 16,
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
  },
})
