package cn.linkdeck.adb;

import android.app.AlertDialog;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.WrapperListAdapter;

/** Decorates the platform's item views; preserves native layout, IDs, enabled state and click handling. */
final class DialogListTint {
 static void beforeFirstDraw(final AlertDialog dialog){
  final View decor=dialog.getWindow().getDecorView();
  decor.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
   public boolean onPreDraw(){
    ListView list=dialog.getListView();
    if(list!=null&&list.getAdapter()!=null){
     ListAdapter original=list.getAdapter();
     boolean replaced=!(original instanceof TintedAdapter);
     if(replaced)list.setAdapter(new TintedAdapter(original));
     // Already attached/recycled children are also recolored before rendering.
     for(int i=0;i<list.getChildCount();i++)tint(list.getChildAt(i));
     decor.getViewTreeObserver().removeOnPreDrawListener(this);
     return !replaced;
    }
    return true;
   }
  });
 }
 static void tint(View view){
  if(view instanceof TextView)((TextView)view).setTextColor(UiPalette.color(0xffedf5ff));
  if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)tint(group.getChildAt(i));}
 }
 static final class TintedAdapter extends BaseAdapter implements WrapperListAdapter {
  private final ListAdapter source;
  TintedAdapter(ListAdapter source){this.source=source;}
  public ListAdapter getWrappedAdapter(){return source;}
  public int getCount(){return source.getCount();}
  public Object getItem(int position){return source.getItem(position);}
  public long getItemId(int position){return source.getItemId(position);}
  public boolean hasStableIds(){return source.hasStableIds();}
  public boolean areAllItemsEnabled(){return source.areAllItemsEnabled();}
  public boolean isEnabled(int position){return source.isEnabled(position);}
  public int getItemViewType(int position){return source.getItemViewType(position);}
  public int getViewTypeCount(){return source.getViewTypeCount();}
  public boolean isEmpty(){return source.isEmpty();}
  public void registerDataSetObserver(DataSetObserver observer){source.registerDataSetObserver(observer);}
  public void unregisterDataSetObserver(DataSetObserver observer){source.unregisterDataSetObserver(observer);}
  public View getView(int position,View recycled,ViewGroup parent){View row=source.getView(position,recycled,parent);tint(row);return row;}
 }
 private DialogListTint(){}
}
