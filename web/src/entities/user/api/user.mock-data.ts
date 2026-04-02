import type { User } from "@/shared/types/user"
import type { UserId } from "@/shared/types/ids"

export const mockUsers: User[] = [
  {
    id: "0193a5b0-000e-7000-8000-000000000001" as UserId,
    login: "jsmith",
    name: "John Smith",
    active: true,
  },
  {
    id: "0193a5b0-000e-7000-8000-000000000002" as UserId,
    login: "mjones",
    name: "Maria Jones",
    active: true,
  },
  {
    id: "0193a5b0-000e-7000-8000-000000000003" as UserId,
    login: "bwilson",
    name: "Bob Wilson",
    active: false,
  },
]
