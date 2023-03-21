package de.devtal.doorble;
import android.widget.AutoCompleteTextView;

public class SuggestionTextView extends AutoCompleteTextView {
	public SuggestionTextView(android.content.Context context){
		super(context);
	}
	public SuggestionTextView(android.content.Context arg0, android.util.AttributeSet arg1) {
		super(arg0, arg1);
	}

	public SuggestionTextView(android.content.Context arg0, android.util.AttributeSet arg1, int arg2) {
		super(arg0, arg1, arg2);
	}

	@Override
	public boolean enoughToFilter() {
		return true;
	}

	@Override
	protected void onFocusChanged(boolean focused, int direction,
			android.graphics.Rect previouslyFocusedRect) {
		super.onFocusChanged(focused, direction, previouslyFocusedRect);
		if(focused && getAdapter() != null){
			performFiltering(getText(), 0);
		}
	}
}

