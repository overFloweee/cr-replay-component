# SPDX-License-Identifier: Apache-2.0
# Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
"""Host isolation patches extracted from the fixed main build; generated game code stays local."""

def patch_host(decoded):
     # 在游戏宿主原生初始化之前添加素材目录。
     game=decoded/'smali/com/supercell/clashroyale/GameApp.smali'
     text=game.read_text();text=text.replace('    invoke-super {p0, p1}, Lcom/supercell/titan/GameApp;->onCreate(Landroid/os/Bundle;)V','    invoke-static {p0}, Lcn/crhistory/player/ExternalAssets;->attach(Landroid/content/Context;)V\n    invoke-super {p0, p1}, Lcom/supercell/titan/GameApp;->onCreate(Landroid/os/Bundle;)V',1);game.write_text(text)
     titan=decoded/'smali/com/supercell/titan/GameApp.smali';text=titan.read_text()
     start=text.index('.method public static getAPKPath()Ljava/lang/String;');end=text.index('.end method',start)+len('.end method')
     text=text[:start]+'''.method public static getAPKPath()Ljava/lang/String;
       .locals 1
       invoke-static {}, Lcom/supercell/titan/GameApp;->getInstance()Lcom/supercell/titan/GameApp;
       move-result-object v0
       invoke-static {v0}, Lcn/crhistory/player/ExternalAssets;->archivePath(Landroid/content/Context;)Ljava/lang/String;
       move-result-object v0
       return-object v0
    .end method'''+text[end:];titan.write_text(text)
     text=game.read_text()
     if 'attachBaseContext(Landroid/content/Context;)V' in text:raise ValueError('GameApp 初始化路径已变化，需复核隔离注入')
     text+='''
    .method protected attachBaseContext(Landroid/content/Context;)V
       .locals 1
       invoke-static {p1}, Lcn/crhistory/player/ExternalAssets;->engineContext(Landroid/content/Context;)Landroid/content/Context;
       move-result-object v0
       invoke-super {p0, v0}, Lcom/supercell/titan/GameApp;->attachBaseContext(Landroid/content/Context;)V
       return-void
    .end method
    .method public getFilesDir()Ljava/io/File;
       .locals 1
       invoke-static {p0}, Lcn/crhistory/player/ExternalAssets;->files(Landroid/content/Context;)Ljava/io/File;
       move-result-object v0
       return-object v0
    .end method
    .method public getApplicationContext()Landroid/content/Context;
       .locals 1
       invoke-super {p0}, Lcom/supercell/titan/GameApp;->getApplicationContext()Landroid/content/Context;
       move-result-object v0
       invoke-static {v0}, Lcn/crhistory/player/ExternalAssets;->engineContext(Landroid/content/Context;)Landroid/content/Context;
       move-result-object v0
       return-object v0
    .end method
    ''';game.write_text(text)
     application=decoded/'smali/com/supercell/titan/TitanApplication.smali'
     text=application.read_text()
     if '.method protected attachBaseContext(Landroid/content/Context;)V' in text:raise ValueError('TitanApplication 初始化路径已变化，需复核隔离注入')
     text+='''
    .method protected attachBaseContext(Landroid/content/Context;)V
       .locals 1
       invoke-static {p1}, Lcn/crhistory/player/ExternalAssets;->applicationContext(Landroid/content/Context;)Landroid/content/Context;
       move-result-object v0
       invoke-super {p0, v0}, Landroid/app/Application;->attachBaseContext(Landroid/content/Context;)V
       return-void
    .end method
    '''
     application.write_text(text)
