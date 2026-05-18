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

// Bun's isolated install layout puts transitive deps at
// node_modules/.bun/<name>@<hash>/node_modules/<name>. Leaving hierarchical
// lookup ON lets Metro walk up into those nested node_modules so peer deps
// declared by sub-packages still resolve without us hoisting every one to
// apps/mobile/node_modules.
config.resolver.disableHierarchicalLookup = false

module.exports = config
