import { defineConfig } from "oxlint";

import core from "ultracite/oxlint/core";
import react from "ultracite/oxlint/react";
import vitest from "ultracite/oxlint/vitest";
import remix from "ultracite/oxlint/remix";

export default defineConfig({
  extends: [
    core,
    react,
    vitest,
    remix,
  ],
});
