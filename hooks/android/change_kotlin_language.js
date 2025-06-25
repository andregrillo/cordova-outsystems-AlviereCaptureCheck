#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

module.exports = function (context) {
  const projectRoot = context.opts.projectRoot;

  // === 1. Modify root build.gradle (classpath) ===
  const androidPath = path.join(projectRoot, 'platforms', 'android');
  const rootGradlePath = path.join(androidPath, 'build.gradle');
  
  console.log('[androidPath] '+androidPath);
  console.log('[rootGradlePath] '+rootGradlePath);

  // === 3. Modify cdv-gradle-config.json in root ===
  const configJsonPath = path.join(androidPath, 'cdv-gradle-config.json');
  if (fs.existsSync(configJsonPath)) {
    const config = JSON.parse(fs.readFileSync(configJsonPath, 'utf8'));
    const oldVersion = config.KOTLIN_VERSION;

    config.KOTLIN_VERSION = '2.2.0';
    fs.writeFileSync(configJsonPath, JSON.stringify(config, null, 2), 'utf8');
    console.log(`[compose-hook] ✅ Updated KOTLIN_VERSION from "${oldVersion}" to "2.2.0" in cdv-gradle-config.json`);
  } else {
    console.warn('[compose-hook] ⚠️ cdv-gradle-config.json not found');
  }

};