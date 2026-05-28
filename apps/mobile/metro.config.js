// Expo SDK 52+ auto-detects the monorepo root and resolves workspace packages,
// so manual watchFolders / resolver.nodeModulesPaths / disableHierarchicalLookup
// are no longer needed (and would conflict with the built-in detection).

const { getDefaultConfig } = require("expo/metro-config")

const config = getDefaultConfig(__dirname)

module.exports = config
