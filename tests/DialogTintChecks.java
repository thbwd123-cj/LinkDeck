package cn.linkdeck.adb;
import android.view.*;import android.widget.*;import android.database.*;import android.app.AlertDialog;
public final class DialogTintChecks {
 static int checks;
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 static final class Source extends BaseAdapter {
  final String[] items={"界面与配色","后台连接设置","任务与记录","恢复中心","设备能力报告","连接向导与支持范围","关于轻连","开源组件与许可"};DataSetObserver observer;int reused;
  public int getCount(){return items.length;}public Object getItem(int p){return items[p];}public long getItemId(int p){return 900+p;}public boolean hasStableIds(){return true;}public boolean areAllItemsEnabled(){return false;}public boolean isEnabled(int p){return p!=3;}public int getItemViewType(int p){return p%2;}public int getViewTypeCount(){return 2;}public boolean isEmpty(){return false;}
  public void registerDataSetObserver(DataSetObserver o){observer=o;}public void unregisterDataSetObserver(DataSetObserver o){if(observer==o)observer=null;}
  public View getView(int position,View recycled,ViewGroup parent){TextView v;if(recycled!=null){reused++;v=(TextView)recycled;}else v=new TextView();v.text=items[position];v.height=64;v.tag=items[position];v.color=0xffffffff;return v;}
 }
 public static void main(String[] args){
  Source source=new Source();AlertDialog dialog=new AlertDialog();dialog.list.setAdapter(source);Object clicks=dialog.list.clickListener;TextView untouchedTitle=new TextView();untouchedTitle.color=123;
  UiPalette.select("light");DialogListTint.beforeFirstDraw(dialog);
  ViewTreeObserver.OnPreDrawListener first=dialog.window.decor.tree.listener;
  check(!first.onPreDraw(),"Unstyled first frame deferred until native list relayout");
  check(dialog.window.decor.tree.listener==null,"First draw hook removes itself");
  check(dialog.list.clickListener==clicks,"Click listener preserved");
  DialogListTint.TintedAdapter adapter=(DialogListTint.TintedAdapter)dialog.list.getAdapter();check(adapter.getWrappedAdapter()==source,"Original layout adapter preserved");
  TextView recycled=null;
  for(String theme:UiPalette.IDS){UiPalette.select(theme);
   for(int i=0;i<8;i++){TextView row=(TextView)adapter.getView(i,recycled,dialog.list);check(row.color==UiPalette.color(0xffedf5ff),"Foreground follows current palette");if(theme.equals("light"))check(row.color==0xff172b3a,"Cloud white uses dark ink");check(row.text.equals(source.items[i])&&row.tag==source.items[i]&&row.height==64,"Text, identity and row size preserved");if(recycled!=null)check(row==recycled,"Native recycled view returned unchanged");recycled=row;}
  }
  check(source.reused==39,"Recycled rows tinted on every bind");check(adapter.getCount()==8&&!adapter.isEmpty(),"Item count retained");check(adapter.hasStableIds()&&!adapter.areAllItemsEnabled(),"Adapter flags retained");
  for(int i=0;i<8;i++){check(adapter.getItem(i)==source.items[i]&&adapter.getItemId(i)==900+i,"Item IDs retained");check(adapter.isEnabled(i)==(i!=3)&&adapter.getItemViewType(i)==i%2,"Enabled states and view types retained");}check(adapter.getViewTypeCount()==2,"View type count retained");
  DataSetObserver o=new DataSetObserver();adapter.registerDataSetObserver(o);check(source.observer==o,"Observer registered on original");adapter.unregisterDataSetObserver(o);check(source.observer==null,"Observer removed from original");
  ViewGroup nested=new ViewGroup();nested.children.add(recycled);UiPalette.select("light");DialogListTint.tint(nested);check(recycled.color==0xff172b3a,"Nested native text tinted");check(untouchedTitle.color==123,"Unrelated title not recolored");
  CheckedTextView choice=new CheckedTextView();choice.color=0xffffffff;UiPalette.select("light");DialogListTint.tint(choice);check(choice.color==0xff172b3a,"Single/multi-choice row tinted");
  System.out.println("DIALOG TINT HOST PASSED: "+checks+" checks (not Android device rendering)");
 }
}
