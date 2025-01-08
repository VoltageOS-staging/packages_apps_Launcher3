/*
 * Copyright (C) 2019 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3;

import android.app.WallpaperManager;
import android.os.Bundle;

import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.wallpaper.WallpaperDatabase;
import com.android.launcher3.wallpaper.WallpaperService;

public class CustomLauncher extends QuickstepLauncher {

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        WallpaperDatabase.INSTANCE.get(this).checkpointSync();
        
        // Save current wallpaper on first launch if database is empty
        new Thread(() -> {
            try {
                if (WallpaperService.INSTANCE.get(this).getTopWallpapers().isEmpty()) {
                    WallpaperManager wallpaperManager = WallpaperManager.getInstance(this);
                    WallpaperService.INSTANCE.get(this).saveWallpaper(wallpaperManager);
                }
            } catch (Exception e) {
                android.util.Log.e("CustomLauncher", "Error saving initial wallpaper", e);
            }
        }).start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

}
