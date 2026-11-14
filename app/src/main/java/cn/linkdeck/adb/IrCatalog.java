package cn.linkdeck.adb;

import android.content.res.AssetManager;
import java.io.*;
import java.util.*;

/** Small offline candidate catalog. Every item names its exact source remote model. */
public final class IrCatalog {
  public static final class Entry {
    public final String brand, model, file;
    Entry(String brand,String model,String file){this.brand=brand;this.model=model;this.file=file;}
  }
  private final AssetManager assets;
  private final List<Entry> entries=new ArrayList<>();
  public IrCatalog(AssetManager assets) throws IOException {
    this.assets=assets;
    try(BufferedReader reader=new BufferedReader(new InputStreamReader(assets.open("ir/catalog.tsv"),"UTF-8"))){
      String line;
      while((line=reader.readLine())!=null){
        String[] fields=line.split("\t");
        if(fields.length!=3||!fields[2].matches("[A-Za-z0-9_-]+"))continue;
        entries.add(new Entry(fields[0],fields[1],fields[2]));
      }
    }
  }
  public List<String> brands(){LinkedHashSet<String> names=new LinkedHashSet<>();for(Entry e:entries)names.add(e.brand);return new ArrayList<>(names);}
  public List<Entry> byBrand(String brand){List<Entry> out=new ArrayList<>();for(Entry e:entries)if(e.brand.equals(brand))out.add(e);return out;}
  public Entry find(String file){for(Entry e:entries)if(e.file.equals(file))return e;return null;}
  public IrCodes load(Entry entry) throws IOException {
    if(entry==null||find(entry.file)==null)throw new IOException("未知遥控器型号");
    try(InputStream in=assets.open("ir/"+entry.file+".ir");ByteArrayOutputStream out=new ByteArrayOutputStream()){
      byte[] buffer=new byte[4096];int n;
      while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>65536)throw new IOException("遥控码过大");}
      return IrCodes.fromFlipper(out.toString("UTF-8"));
    }
  }
}
