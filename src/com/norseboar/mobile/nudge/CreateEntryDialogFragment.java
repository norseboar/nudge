package com.norseboar.mobile.nudge;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

public class CreateEntryDialogFragment extends DialogFragment {
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState){
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.create_entry);

		LayoutInflater inflater = getActivity().getLayoutInflater();
		final View v = inflater.inflate(R.layout.dialog_create_entry, null);
		builder.setView(v);
		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int id) {
				// If user clicks ok, create a new NudgeEntry and add it to Nudge
				EditText nameET = (EditText) v.findViewById(R.id.entry_name_ET);
				EditText locET = (EditText) v.findViewById(R.id.entry_loc_ET);
				NudgeActivity na = (NudgeActivity)getActivity();
				NudgeEntry newEntry = new NudgeEntry(na, nameET.getText().toString(), locET.getText().toString());

				newEntry.checkLocationInfo(na.getLatEstimate(), na.getLonEstimate());
				
				na.addEntry(newEntry);
			}
		});
		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				// Do nothing if user cancels, dialog ends
			}
		});
		
		return builder.create();
	}
}
