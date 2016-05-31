package edu.umd.smile.util;

import java.io.IOException;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetLoader;

public class TextAssetLoader implements AssetLoader {

    @Override
    public Object load(AssetInfo assetInfo) throws IOException {
        return assetInfo.openStream();
    }

}
