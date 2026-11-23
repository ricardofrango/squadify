// The production bundle is large (about 11 MB of JS before gzip), and minifying it with webpack's
// defaults (a source map, and terser on every CPU at once) needs more memory than the GitHub runner
// has: the runner was shut down mid-build (TECHNICAL_DECISIONS.md §13). Nothing reads the source maps
// of the deployed site, so they're skipped, and terser runs on one thread.
if (config.mode === "production") {
    const TerserPlugin = require("terser-webpack-plugin");
    config.devtool = false;
    config.optimization = config.optimization || {};
    config.optimization.minimizer = [new TerserPlugin({ parallel: false })];
}
