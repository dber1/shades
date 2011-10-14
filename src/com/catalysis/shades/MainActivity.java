package com.catalysis.shades;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity
{
	CameraSurface cameraSurface;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		
		cameraSurface = new CameraSurface(this);
		
		setContentView(cameraSurface);
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		
		cameraSurface.onPause();
	}
	
	@Override 
	public void onResume()
	{
		super.onResume();
		
		cameraSurface.onResume();
	}
}