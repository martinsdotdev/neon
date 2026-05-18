import { createApiClient } from "@neon/client/client"
import * as SecureStore from "expo-secure-store"

const TOKEN_KEY = "neon.session"

export const getAuthToken = async (): Promise<string | null> =>
  await SecureStore.getItemAsync(TOKEN_KEY)

export const setAuthToken = async (token: string): Promise<void> => {
  await SecureStore.setItemAsync(TOKEN_KEY, token)
}

export const clearAuthToken = async (): Promise<void> => {
  await SecureStore.deleteItemAsync(TOKEN_KEY)
}

// EXPO_PUBLIC_API_BASE_URL must be set per environment:
//   - Android emulator: http://10.0.2.2:8080
//   - iOS simulator:    http://localhost:8080
//   - Physical device:  http://<host-lan-ip>:8080
const baseUrl = process.env.EXPO_PUBLIC_API_BASE_URL ?? ""

export const apiClient = createApiClient({
  baseUrl,
  getAuthToken,
})
