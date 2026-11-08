#!/usr/bin/env python3
"""Generuje projekt moda dla Minecrafta 1.21.4 z kodu 26.x (katalog nebula-client-1214/).

1.21.4 ma obfuskowany kod (mapowania Mojanga), inne API rysowania (GuiGraphics) i eventow myszy/klawiszy.
Wiekszosc kodu jest wspolna, wiec zamiast kopii utrzymywanej recznie robimy tlumaczenie przy kazdym buildzie.
"""
import os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'nebula-client')
MC = sys.argv[1] if len(sys.argv) > 1 else '1.21.4'
DST = os.path.join(ROOT, 'nebula-client-' + MC.replace('.', ''))
TPL = os.path.join(ROOT, 'tools', 'port1214')

if os.path.exists(DST):
    shutil.rmtree(DST)
shutil.copytree(SRC, DST, ignore=shutil.ignore_patterns('build', '.gradle', '*.log'))

JAVA = os.path.join(DST, 'src', 'main', 'java', 'dev', 'nebula', 'client')

OLD = MC.startswith('1.21.4')

MID_RULES = [
    (r'extractBlurredBackground\(g\)', 'renderBlurredBackground(g)'),
    (r'net\.fabricmc\.fabric\.api\.client\.keymapping\.v1\.KeyMappingHelper', 'net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper'),
    (r'KeyMappingHelper\.registerKeyMapping\(', 'KeyBindingHelper.registerKeyBinding('),
]

OLD_RULES = [
            (r'extractBlurredBackground\(g\)', 'renderBlurredBackground()'),
            (r'g\.pose\(\)\.pushMatrix\(\)', 'g.pose().pushPose()'),
            (r'g\.pose\(\)\.popMatrix\(\)', 'g.pose().popPose()'),
            (r'g\.pose\(\)\.scale\(s, s\)', 'g.pose().scale(s, s, 1f)'),
            (r'g\.pose\(\)\.scale\(sx, sy\)', 'g.pose().scale(sx, sy, 1f)'),
            (r'g\.pose\(\)\.translate\(x1, y1\)', 'g.pose().translate(x1, y1, 0f)'),
            (r'g\.pose\(\)\.rotate\((.+?)\);', r'g.pose().mulPose(new org.joml.Quaternionf().rotateZ(\1));'),
            # eventy myszy i klawiszy -> sygnatury 1.21.4
            (r'import net\.minecraft\.client\.input\.\w+;\n', ''),
            (r'mouseClicked\(MouseButtonEvent event, boolean doubleClick\)', 'mouseClicked(double ex, double ey, int eb)'),
            (r'super\.mouseClicked\(event, doubleClick\)', 'super.mouseClicked(ex, ey, eb)'),
            (r'mouseDragged\(MouseButtonEvent event, double dragX, double dragY\)', 'mouseDragged(double ex, double ey, int eb, double dragX, double dragY)'),
            (r'super\.mouseDragged\(event, dragX, dragY\)', 'super.mouseDragged(ex, ey, eb, dragX, dragY)'),
            (r'mouseReleased\(MouseButtonEvent event\)', 'mouseReleased(double ex, double ey, int eb)'),
            (r'super\.mouseReleased\(event\)', 'super.mouseReleased(ex, ey, eb)'),
            (r'event\.x\(\)', 'ex'),
            (r'event\.y\(\)', 'ey'),
            (r'event\.button\(\)', 'eb'),
            (r'keyPressed\(KeyEvent event\)', 'keyPressed(int keyCode, int scanCode, int modifiers)'),
            (r'super\.keyPressed\(event\)', 'super.keyPressed(keyCode, scanCode, modifiers)'),
            (r'event\.key\(\)', 'keyCode'),
            (r'charTyped\(CharacterEvent event\)', 'charTyped(char chr, int mods)'),
            (r'event\.isAllowedChatCharacter\(\)', 'net.minecraft.util.StringUtil.isAllowedChatCharacter(chr)'),
            (r'event\.codepointAsString\(\)', 'String.valueOf(chr)'),
            (r'super\.charTyped\(event\)', 'super.charTyped(chr, mods)'),
            # klawisze
            (r'net\.fabricmc\.fabric\.api\.client\.keymapping\.v1\.KeyMappingHelper', 'net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper'),
            (r'KeyMappingHelper\.registerKeyMapping\(', 'KeyBindingHelper.registerKeyBinding('),
            (r'registerKey\(KeyMapping\.Category category\)', 'registerKey(String category)'),
]

def sub(text, pairs):
    for a, b in pairs:
        text = re.sub(a, b, text, flags=re.S)
    return text

for dp, _, files in os.walk(os.path.join(DST, 'src', 'main', 'java')):
    for f in files:
        if not f.endswith('.java'):
            continue
        p = os.path.join(dp, f)
        t = open(p, encoding='utf-8').read()
        t = sub(t, [
            (r'GuiGraphicsExtractor', 'GuiGraphics'),
            (r'\bg\.outline\(', 'g.renderOutline('),
            (r'\bg\.text\(', 'g.drawString('),
            (r'\bg\.item\(', 'g.renderItem('),
            (r'\bg\.itemDecorations\(', 'g.renderItemDecorations('),
            (r'public void extractRenderState\(', 'public void render('),
        ] + (OLD_RULES if OLD else MID_RULES))
        open(p, 'w', encoding='utf-8').write(t)

# pliki, ktore w 1.21.4 wygladaja inaczej
for name, dest in ([('NebulaClient.java', ''), ('LevelRendererMixin.java', 'mixin'), ('ItemInHandRendererMixin.java', 'mixin')] if OLD else []):
    shutil.copy(os.path.join(TPL, name), os.path.join(JAVA, dest, name))

# warstwa zgodnosci: w 1.21.4 ekran jest w Minecraft.screen
compat_dst = os.path.join(DST, 'src', 'main', 'java', 'dev', 'nebula', 'client', 'Compat.java')
ct = open(os.path.join(SRC, 'src', 'compat', 'classic', 'java', 'dev', 'nebula', 'client', 'Compat.java'), encoding='utf-8').read()
if OLD:
    ct = ct.replace('.position()', '.getPosition()').replace('.yRot()', '.getYRot()').replace('.xRot()', '.getXRot()')
open(compat_dst, 'w', encoding='utf-8').write(ct)

# build.gradle i gradle.properties dla starego Loom (z mapowaniami)
open(os.path.join(DST, 'build.gradle'), 'w').write('''plugins {
	id 'fabric-loom' version "${loom_version}"
}

version = project.mod_version
group = project.maven_group

base {
	archivesName = project.archives_base_name
}

dependencies {
	minecraft "com.mojang:minecraft:${project.minecraft_version}"
	mappings loom.officialMojangMappings()

	modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
	modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"
}

processResources {
	inputs.property "version", project.version
	inputs.property "minecraft_dep", project.minecraft_dep

	filesMatching("fabric.mod.json") {
		expand "version": inputs.properties.version, "minecraft_dep": project.minecraft_dep
	}
}

tasks.withType(JavaCompile).configureEach {
	it.options.release = 21
	it.options.encoding = 'UTF-8'
}

java {
	withSourcesJar()
	sourceCompatibility = JavaVersion.VERSION_21
	targetCompatibility = JavaVersion.VERSION_21
}
''')
open(os.path.join(DST, 'gradle.properties'), 'w').write('''org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true

minecraft_version=@MC@
loader_version=0.16.14
loom_version=1.14-SNAPSHOT
minecraft_dep=~@MC@
fabric_api_version=0.119.4+1.21.4

mod_version=0.1.0
maven_group=dev.nebula
archives_base_name=nebula-client
'''.replace('@MC@', MC))
# fabric.mod.json: Java 21
mj = os.path.join(DST, 'src', 'main', 'resources', 'fabric.mod.json')
t = open(mj, encoding='utf-8').read().replace('">=25"', '">=21"').replace('">=0.19.3"', '">=0.16.0"')
open(mj, 'w', encoding='utf-8').write(t)

# 1.21.11: tekst na niebie i pasek Spotify = oryginalny mod LyricsFlow (374m, za zgoda), wbudowany jako jar-in-jar
if MC == '1.21.11':
    import json
    LF = os.path.join(TPL, 'lyricsflow')
    for f in ('module/impl/MusicModule.java', 'module/impl/SkyLyricsModule.java'):
        os.remove(os.path.join(JAVA, f))
    mm = os.path.join(JAVA, 'module', 'ModuleManager.java')
    t = open(mm, encoding='utf-8').read()
    t = re.sub(r'\n\s*register\(new (MusicModule|SkyLyricsModule)\(\)\);', '', t)
    t = re.sub(r'import dev\.nebula\.client\.module\.impl\.(MusicModule|SkyLyricsModule);\n', '', t)
    open(mm, 'w', encoding='utf-8').write(t)
    jars = os.path.join(DST, 'src', 'main', 'resources', 'META-INF', 'jars')
    os.makedirs(jars, exist_ok=True)
    shutil.copy(os.path.join(LF, 'lyricsflow.jar'), os.path.join(jars, 'lyricsflow.jar'))
    shutil.copy(os.path.join(LF, 'NebulaPreLaunch.java'), os.path.join(JAVA, 'NebulaPreLaunch.java'))
    mj2 = os.path.join(DST, 'src', 'main', 'resources', 'fabric.mod.json')
    d = json.load(open(mj2, encoding='utf-8'))
    d['jars'] = [{'file': 'META-INF/jars/lyricsflow.jar'}]
    d['entrypoints']['preLaunch'] = ['dev.nebula.client.NebulaPreLaunch']
    json.dump(d, open(mj2, 'w', encoding='utf-8'), indent=2)
    # zakladka "Spotify" w menu Nebuli: ustawienia modu kolegi (kompilacja z jego jarem, w runtime jest w jar-in-jar)
    libs = os.path.join(DST, 'libs')
    os.makedirs(libs, exist_ok=True)
    shutil.copy(os.path.join(LF, 'lyricsflow.jar'), os.path.join(libs, 'lyricsflow.jar'))
    shutil.copy(os.path.join(LF, 'LyricsFlowModule.java'), os.path.join(JAVA, 'module', 'impl', 'LyricsFlowModule.java'))
    bg = os.path.join(DST, 'build.gradle')
    t = open(bg, encoding='utf-8').read()
    t = t.replace('modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"',
                  'modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"\n\tcompileOnly files("libs/lyricsflow.jar")', 1)
    assert 'libs/lyricsflow.jar' in t
    open(bg, 'w', encoding='utf-8').write(t)
    t = open(mm, encoding='utf-8').read()
    t = t.replace('register(new ToggleKeysModule());', 'register(new ToggleKeysModule());\n\t\tregister(new LyricsFlowModule());', 1)
    t = t.replace('import dev.nebula.client.module.impl.FpsModule;', 'import dev.nebula.client.module.impl.FpsModule;\nimport dev.nebula.client.module.impl.LyricsFlowModule;', 1)
    assert 'LyricsFlowModule;' in t and 'new LyricsFlowModule' in t
    open(mm, 'w', encoding='utf-8').write(t)
# mixin: bez mixinow z width (brak parametru) - ich brak jest bezpieczny dzieki defaultRequire 0
print('OK: wygenerowano', DST)
