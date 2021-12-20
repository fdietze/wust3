const webpack = require("webpack");
const {webDev} = require("@fun-stack/fun-pack");

const config = webDev({
  indexHtml: "src/main/html/index.html",
  assetsDir: "assets",
  extraWatchDirs: [
    // "local" // frontend with local backend
    // "../terraform/.terraform/modules/uglot/serve/" // frontend with deployed backend
  ]
});

//config.plugins.push(
//   new webpack.DefinePlugin({
//      "TESTENV": "hello"//JSON.stringify(process.env.TESTENV)
//    })
//);

module.exports = config;
