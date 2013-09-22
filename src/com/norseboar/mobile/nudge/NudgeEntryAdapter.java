package com.norseboar.mobile.nudge;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;


public class NudgeEntryAdapter extends ArrayAdapter<NudgeEntry> {

	static final int LR_ID = R.layout.nudge_entry_row;
	Context c;
	List<NudgeEntry> data = null;
	
	public NudgeEntryAdapter(Context c, List<NudgeEntry> data) {
		super(c, LR_ID, data);
		this.c = c;
		this.data = data;
	}
	
	static class ViewHolder {
		protected TextView nameText;
		protected TextView locText;
	}
	
	@Override
	public View getView(int pos, View convertView, ViewGroup parent){
		View row = convertView;
		
		if(row == null){
			LayoutInflater inflater = ((Activity)c).getLayoutInflater();
			row = inflater.inflate(LR_ID, parent, false);
			
			final ViewHolder viewholder = new ViewHolder();
			viewholder.nameText = (TextView)row.findViewById(R.id.entry_name);
			viewholder.locText = (TextView)row.findViewById(R.id.entry_loc);

			row.setTag(viewholder);
		}
		
		NudgeEntry entry = data.get(pos);
		ViewHolder vh = (ViewHolder) row.getTag();
		vh.nameText.setText(entry.getName());
		
// TODO: uncomment, get strikethrough and such back
//		// If item is checked off, strike through it
//		if(entry.isCompleted()){
//			vh.nameText.setPaintFlags(vh.nameText.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
//		}
//		else{
//			vh.nameText.setPaintFlags(0);
//		}
		
		vh.locText.setText(entry.getLoc());
		
		return row;
		
	}
	
	@Override
	public void remove(NudgeEntry ne){
		ne.setCompleted(true);
		ne.getActivity().saveList();
		super.remove(ne);
	}
}
