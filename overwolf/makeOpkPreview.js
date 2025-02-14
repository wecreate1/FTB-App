const fs = require('fs');
const childProcess = require('child_process');
let manifestFile = fs.readFileSync('manifest.json', 'utf-8');
let manifestData = JSON.parse(manifestFile);
let version = manifestData.meta.version;

function makeOPK() {
  return childProcess.execSync(
    'zip -9 -r "' +
      manifestData.meta.name +
      ' - ' +
      version +
      '.opk" dist/ jdk-17.0.1+12-minimal/ OverwolfShim.dll ' +
      manifestData.meta.icon +
      ' *.jar launchericon.ico manifest.json version.json -x windows/*',
  );
}

makeOPK();
