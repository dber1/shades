package com.catalysis.shades;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import android.opengl.Matrix;
import android.util.Log;
import android.view.SurfaceHolder;
import android.opengl.GLSurfaceView.Renderer;

public class CameraSurface extends GLSurfaceView implements Renderer, PreviewCallback
{
  private static final int FLOAT_SIZE_BYTES = 4;
  private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
  private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
  private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
  private float[] mTriangleVerticesData = {
          // X, Y, Z, U, V
          0.0f,  0f, 0, 0f, 0.0f,
          1.0f,  0f, 0, 1f, 0f,
          0.0f,  1f, 0, 0f,  1f,
          1.0f,  1f, 0, 1f,  1f,
          };
  
  private float[] mProjMatrix = new float[16];
	
  private FloatBuffer mTriangleVertices;
  
	private static final String TAG = "Shades";
	private Camera camera;
	
	FloatBuffer cubeBuff;
	FloatBuffer texBuff;
	byte[] glCameraFrame = new byte[256*256];
	int[] cameraTexture;
	
  private int mProgram;
  private int mTextureID;
  private int muMVPMatrixHandle;
  private int maPositionHandle;
  private int maTextureHandle;
	
	private long prevFrame = 0;
	private long frame = 0;

	
	public CameraSurface(Context context)
	{
		super(context);
		
		setEGLContextClientVersion(2);
    setEGLConfigChooser(false);
    setRenderer(this);
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
		
    // Ignore the passed-in GL10 interface, and use the GLES20
    // class's static methods instead.
    mProgram = createProgram(readShaderSource(true), readShaderSource(false));
    
		maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
		checkGlError("glGetAttribLocation aPosition");

		maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
		checkGlError("glGetAttribLocation aTextureCoord");

		muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
		checkGlError("glGetUniformLocation uMVPMatrix");
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height)
	{
		GLES20.glViewport(0, 0, width, height);

    Matrix.orthoM(mProjMatrix, 0, 0, width, height, 0, -1, 1);
    
    mTriangleVerticesData = new float[] {
                                             // X, Y, Z, U, V
                                             0.0f,  0f, 0, 0f, 0.0f,
                                             width,  0f, 0, 1f, 0f,
                                             0.0f,  height, 0, 0f,  1f,
                                             width,  height, 0, 1f,  1f,
                                             };
    
    mTriangleVertices = ByteBuffer.allocateDirect(mTriangleVerticesData.length * FLOAT_SIZE_BYTES)
    	.order(ByteOrder.nativeOrder()).asFloatBuffer();
    
    mTriangleVertices.put(mTriangleVerticesData).position(0);
    
    checkGlError("surfaceChanged");
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

    GLES20.glClearColor(0.0f, 0.0f, 1.0f, 1.0f);
    GLES20.glClear( GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
    GLES20.glUseProgram(mProgram);
    checkGlError("glUseProgram");

    bindCameraTexture();
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    
    mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
    GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
    checkGlError("glVertexAttribPointer maPosition");
    
    mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
    GLES20.glEnableVertexAttribArray(maPositionHandle);
    checkGlError("glEnableVertexAttribArray maPositionHandle");
    
    GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
            TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
    checkGlError("glVertexAttribPointer maTextureHandle");
    
    GLES20.glEnableVertexAttribArray(maTextureHandle);
    checkGlError("glEnableVertexAttribArray maTextureHandle");

    GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mProjMatrix, 0);
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    checkGlError("glDrawArrays");
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
	
	private void bindCameraTexture() 
	{
		synchronized(this) 
		{
			if (cameraTexture == null)
				cameraTexture = new int[1];
			else
				GLES20.glDeleteTextures(1, cameraTexture, 0);
			
			GLES20.glGenTextures(1, cameraTexture, 0);
			mTextureID = cameraTexture[0];
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureID);
			GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, 256, 256, 0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(glCameraFrame));
			GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
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
	
	private String readShaderSource(boolean isVertexShader)
	{
		String result = "";
		
		InputStream stream = getResources().openRawResource(isVertexShader ? R.raw.vertex_shader : R.raw.fragment_shader);
		BufferedReader buffStream = new BufferedReader(new InputStreamReader(stream));
		
		String line;
		try
		{
			line = buffStream.readLine();
			
			while (line != null)
			{
				result += line + "\n";
				line = buffStream.readLine();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		return result;
	}
	
	private void checkGlError(String op)
	{
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR)
		{
			Log.e(TAG, op + ": glError " + error);
			throw new RuntimeException(op + ": glError " + error);
		}
	}
}
