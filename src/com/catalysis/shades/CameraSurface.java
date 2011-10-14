package com.catalysis.shades;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.util.Log;
import android.view.SurfaceHolder;
import android.opengl.GLSurfaceView.Renderer;

public class CameraSurface extends GLSurfaceView implements Renderer, PreviewCallback
{
	float camObjCoord[];
	
	final static float camTexCoords[] = new float[] { 0f, 0f,
	                                         				  1f, 0f,
	                                         				  0f, 1f,
	                                         				  1f, 1f };

	private static final String TAG = "Shades";
	private Camera camera;
	
	FloatBuffer cubeBuff;
	FloatBuffer texBuff;
	byte[] glCameraFrame = new byte[256*256];
	int[] cameraTexture;
	
	private long prevFrame = 0;
	private long frame = 0;
	
	private int shaderProgram;
	
	public CameraSurface(Context context)
	{
		super(context);
		
    setEGLConfigChooser(false);
    setRenderer(this);
	}
	
	FloatBuffer makeFloatBuffer(float[] arr) 
	{
		ByteBuffer bb = ByteBuffer.allocateDirect(arr.length*4);
		bb.order(ByteOrder.nativeOrder());
		FloatBuffer fb = bb.asFloatBuffer();
		fb.put(arr);
		fb.position(0);
		
		return fb;
	}
	
	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
		if (camera != null)
		{
			camera.stopPreview();
			camera.setPreviewCallback(null);
			
			camera.release();
		}
	}
	
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config)
	{
		camera = Camera.open();
		
  	Camera.Parameters p = camera.getParameters();  
  	p.setPreviewSize(240, 160);
  	camera.setParameters(p);
  	
		camera.startPreview();
		camera.setPreviewCallback(this);
		
		gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);

		gl.glClearColor(0, 0, 0, 0);
		gl.glEnable(GL10.GL_CULL_FACE);
		gl.glShadeModel(GL10.GL_SMOOTH);
		gl.glEnable(GL10.GL_DEPTH_TEST);
		
		texBuff = makeFloatBuffer(camTexCoords);		
		gl.glTexCoordPointer(2, GL10.GL_FLOAT, 0, texBuff);
		gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
		
		shaderProgram = createProgram("", "");
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height)
	{
		gl.glViewport(0, 0, width, height);

		gl.glMatrixMode(GL10.GL_PROJECTION);
		gl.glLoadIdentity();
		GLU.gluOrtho2D(gl, width, 0, height, 0);
		
		gl.glMatrixMode(GL10.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		camObjCoord = new float[] {  0,     0,       0f,
                         				 width, 0,       0f,
                         				 0,     height,  0f,
                         				 width, height,  0f };

		cubeBuff = makeFloatBuffer(camObjCoord);
		
		gl.glVertexPointer(3, GL10.GL_FLOAT, 0, cubeBuff);
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
	}

	@Override
	public void onDrawFrame(GL10 gl)
	{
		if ((System.currentTimeMillis() - prevFrame) >= 1000)
		{
			System.out.println("FPS: " + frame);
			frame = 0;
			prevFrame = System.currentTimeMillis();
		}
		
		frame++;
		
		gl.glEnable(GL10.GL_TEXTURE_2D);
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		bindCameraTexture(gl);
		
		GLES20.glUseProgram(shaderProgram);

		gl.glNormal3f(0,0,1);
		gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);	
	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera)
	{
 		int bwCounter=0;
 		int yuvsCounter=0;
 		for (int y=0;y<160;y++) 
 		{
 			System.arraycopy(data, yuvsCounter, glCameraFrame, bwCounter, 240);
 			yuvsCounter=yuvsCounter+240;
 			bwCounter=bwCounter+256;
 		}
	}
	
	private void bindCameraTexture(GL10 gl) 
	{
		synchronized(this) 
		{
			if (cameraTexture == null)
				cameraTexture = new int[1];
			else
				gl.glDeleteTextures(1, cameraTexture, 0);
			
			gl.glGenTextures(1, cameraTexture, 0);
			int tex = cameraTexture[0];
			gl.glBindTexture(GL10.GL_TEXTURE_2D, tex);
			gl.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GL10.GL_LUMINANCE, 256, 256, 0, GL10.GL_LUMINANCE, GL10.GL_UNSIGNED_BYTE, ByteBuffer.wrap(glCameraFrame));
			gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
		}
	}
	
	private int createProgram(String vertexSource, String fragmentSource)
	{
		int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
		int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);

		int program = GLES20.glCreateProgram();
		
		if (program != 0)
		{
			GLES20.glAttachShader(program, vertexShader);
			GLES20.glAttachShader(program, pixelShader);

			GLES20.glLinkProgram(program);
			
			int[] linkStatus = new int[1];
			GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
			
			if (linkStatus[0] != GLES20.GL_TRUE)
			{
				Log.e(TAG, "Could not link program: ");
				Log.e(TAG, GLES20.glGetProgramInfoLog(program));
				
				GLES20.glDeleteProgram(program);
				program = 0;
			}
		}
		
		return program;
	}

	private int loadShader(int shaderType, String source)
	{
		int shader = GLES20.glCreateShader(shaderType);
		
		if (shader != 0)
		{
			GLES20.glShaderSource(shader, source);
			GLES20.glCompileShader(shader);
			
			int[] compiled = new int[1];
			GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
			
			if (compiled[0] == 0)
			{
				Log.e(TAG, "Could not compile shader " + shaderType + ":");
				Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
				GLES20.glDeleteShader(shader);
				shader = 0;
			}
		}
		
		return shader;
	}
}
