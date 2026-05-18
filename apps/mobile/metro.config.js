// Metro configuration for a Bun workspaces monorepo. Tells Metro to watch the
// repository root so workspace packages under `packages/*` are picked up, and
// to resolve `node_modules` from both the app directory and the workspace
// root (Bun hoists most deps to the root).

const { getDefaultConfig } = require("expo/metro-config")
const path = require("node:path")

const projectRoot = __dirname
const workspaceRoot = path.resolve(projectRoot, "..", "..")

const config = getDefaultConfig(projectRoot)

config.watchFolders = [workspaceRoot]

config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, "node_modules"),
  path.resolve(workspaceRoot, "node_modules"),
]

config.resolver.disableHierarchicalLookup = true

module.exports = config
