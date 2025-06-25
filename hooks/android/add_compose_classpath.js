#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

module.exports = function (context) {
  const projectRoot = context.opts.projectRoot;

  // === 1. Modify root build.gradle (classpath) ===
  const androidPath = path.join(projectRoot, 'platforms', 'android');
  const rootGradlePath = path.join(androidPath, 'build.gradle');
  const classpathLine = `        classpath "org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:$cordovaConfig.KOTLIN_VERSION"`;
  console.log('[rootGradlePath] '+rootGradlePath);

  if (fs.existsSync(rootGradlePath)) {
    let content = fs.readFileSync(rootGradlePath, 'utf8');
    if (!content.includes('org.jetbrains.kotlin.plugin.compose.gradle.plugin')) {
      content = content.replace(/dependencies\s*{/, match => `${match}\n${classpathLine}`);
      fs.writeFileSync(rootGradlePath, content, 'utf8');
      console.log('[compose-hook] ✅ Added Compose classpath to root build.gradle');
    } else {
      console.log('[compose-hook] ⚠️ Compose classpath already present in root build.gradle');
    }
  }

  // === 2. Modify :app build.gradle (apply plugins) ===
  const appGradlePath = path.join(androidPath, 'app', 'build.gradle');
  const pluginAndroid = `apply plugin: 'org.jetbrains.kotlin.android'`;
  const pluginCompose = `apply plugin: "org.jetbrains.kotlin.plugin.compose"`;
  console.log('[appGradlePath] '+appGradlePath);

  if (fs.existsSync(appGradlePath)) {
    let content = fs.readFileSync(appGradlePath, 'utf8');
    const lines = content.split('\n');
    const index = lines.findIndex(line => line.includes(`apply plugin: 'com.android.application'`));

    if (index !== -1) {
      // Verifica se já estão adicionadas
      const alreadyHasAndroid = lines.some(line => line.includes(pluginAndroid));
      const alreadyHasCompose = lines.some(line => line.includes(pluginCompose));

      const newLines = [];
      newLines.push(lines[index]); // keep original 'com.android.application'
      if (!alreadyHasAndroid) newLines.push(pluginAndroid);
      if (!alreadyHasCompose) newLines.push(pluginCompose);

      // Substitui a linha original por ela + os novos plugins
      lines.splice(index, 1, ...newLines);

      fs.writeFileSync(appGradlePath, lines.join('\n'), 'utf8');
      console.log('[compose-hook] ✅ Added plugins to app/build.gradle');
    } else {
      console.warn('[compose-hook] ⚠️ Could not find apply plugin: com.android.application in app/build.gradle');
    }
  }
};