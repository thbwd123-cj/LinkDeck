"""Host behavioral test of real DialogListTint with small Android API doubles; not a device render test."""
from pathlib import Path
import subprocess
r=Path(__file__).resolve().parents[1];out=r/'build/dialog-tint';out.mkdir(parents=True,exist_ok=True)
stubs={
'android/database/DataSetObserver.java':'package android.database; public class DataSetObserver {}',
'android/view/View.java':'''package android.view; public class View { public final ViewTreeObserver tree=new ViewTreeObserver();public ViewTreeObserver getViewTreeObserver(){return tree;} public int height=64;public Object tag; }''',
'android/view/ViewTreeObserver.java':'''package android.view; public class ViewTreeObserver {public interface OnPreDrawListener {boolean onPreDraw();}public OnPreDrawListener listener;public void addOnPreDrawListener(OnPreDrawListener l){listener=l;}public void removeOnPreDrawListener(OnPreDrawListener l){if(listener==l)listener=null;}}''',
'android/view/ViewGroup.java':'''package android.view;import java.util.*;public class ViewGroup extends View {public final ArrayList<View> children=new ArrayList<>(); public int getChildCount(){return children.size();}public View getChildAt(int i){return children.get(i);}}''',
'android/view/Window.java':'''package android.view;public class Window {public final View decor=new View();public View getDecorView(){return decor;}}''',
'android/widget/TextView.java':'''package android.widget;public class TextView extends android.view.View {public int color=0xffffffff;public CharSequence text;public void setTextColor(int c){color=c;}}''',
'android/widget/CheckedTextView.java':'''package android.widget;public class CheckedTextView extends TextView {}''',
'android/widget/ListAdapter.java':'''package android.widget;import android.view.*;import android.database.*;public interface ListAdapter {int getCount();Object getItem(int p);long getItemId(int p);boolean hasStableIds();boolean areAllItemsEnabled();boolean isEnabled(int p);int getItemViewType(int p);int getViewTypeCount();boolean isEmpty();void registerDataSetObserver(DataSetObserver o);void unregisterDataSetObserver(DataSetObserver o);View getView(int p,View v,ViewGroup g);}''',
'android/widget/BaseAdapter.java':'''package android.widget;public abstract class BaseAdapter implements ListAdapter {}''',
'android/widget/WrapperListAdapter.java':'''package android.widget;public interface WrapperListAdapter extends ListAdapter {ListAdapter getWrappedAdapter();}''',
'android/widget/ListView.java':'''package android.widget;import android.view.*;public class ListView extends ViewGroup {public ListAdapter adapter;public int binds;public Object clickListener=new Object();public ListAdapter getAdapter(){return adapter;}public void setAdapter(ListAdapter a){adapter=a;binds++;children.clear();}}''',
'android/app/AlertDialog.java':'''package android.app;import android.view.*;import android.widget.*;public class AlertDialog {public final Window window=new Window();public final ListView list=new ListView();public Window getWindow(){return window;}public ListView getListView(){return list;}}''',
}
files=[]
for name,source in stubs.items():
 p=out/'src'/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source);files.append(p)
src=r/'app/src/main/java/cn/linkdeck/adb'
subprocess.run(['java','-jar',str(r/'.toolchain/ecj.jar'),'-8','-nowarn','-encoding','UTF-8','-d',str(out/'classes'),*[str(p) for p in files],str(src/'UiPalette.java'),str(src/'DialogListTint.java'),str(r/'tests/DialogTintChecks.java')],check=True)
subprocess.run(['java','-cp',str(out/'classes'),'cn.linkdeck.adb.DialogTintChecks'],check=True)
builder=(src/'DeckDialogBuilder.java').read_text()
for signature in ['setItems(CharSequence[] items','setItems(int items','setSingleChoiceItems(CharSequence[] items','setSingleChoiceItems(int items','setMultiChoiceItems(CharSequence[] items','setMultiChoiceItems(int items']:
 assert signature in builder
assert 'super.setItems(items,listener)' in builder and 'if(plainItems)DialogListTint.beforeFirstDraw(d)' in builder
print('All native list overloads route through the common tint path')
