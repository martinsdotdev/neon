export const queryKeys = {
  waves: {
    all: ["waves"] as const,
    list: () => [...queryKeys.waves.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.waves.all, "detail", id] as const,
  },
  tasks: {
    all: ["tasks"] as const,
    list: () => [...queryKeys.tasks.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.tasks.all, "detail", id] as const,
  },
  transportOrders: {
    all: ["transportOrders"] as const,
    list: () =>
      [...queryKeys.transportOrders.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.transportOrders.all, "detail", id] as const,
  },
  consolidationGroups: {
    all: ["consolidationGroups"] as const,
    list: () =>
      [...queryKeys.consolidationGroups.all, "list"] as const,
    detail: (id: string) =>
      [
        ...queryKeys.consolidationGroups.all,
        "detail",
        id,
      ] as const,
  },
  orders: {
    all: ["orders"] as const,
    list: () => [...queryKeys.orders.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.orders.all, "detail", id] as const,
  },
  skus: {
    all: ["skus"] as const,
    list: () => [...queryKeys.skus.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.skus.all, "detail", id] as const,
  },
  locations: {
    all: ["locations"] as const,
    list: () => [...queryKeys.locations.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.locations.all, "detail", id] as const,
  },
  workstations: {
    all: ["workstations"] as const,
    list: () =>
      [...queryKeys.workstations.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.workstations.all, "detail", id] as const,
  },
  carriers: {
    all: ["carriers"] as const,
    list: () => [...queryKeys.carriers.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.carriers.all, "detail", id] as const,
  },
  slots: {
    all: ["slots"] as const,
    list: () => [...queryKeys.slots.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.slots.all, "detail", id] as const,
  },
  handlingUnits: {
    all: ["handlingUnits"] as const,
    list: () =>
      [...queryKeys.handlingUnits.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.handlingUnits.all, "detail", id] as const,
  },
  inventory: {
    all: ["inventory"] as const,
    list: () => [...queryKeys.inventory.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.inventory.all, "detail", id] as const,
  },
  users: {
    all: ["users"] as const,
    list: () => [...queryKeys.users.all, "list"] as const,
    detail: (id: string) =>
      [...queryKeys.users.all, "detail", id] as const,
  },
} as const
