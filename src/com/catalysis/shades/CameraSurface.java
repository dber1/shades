package com.catalysis.shades;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

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
	int[] glCameraFrame = new int[256*256];
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
		synchronized(this)
		{
			decodeYUV(glCameraFrame, data, 240, 160);
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
			GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 240, 160, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, IntBuffer.wrap(glCameraFrame));
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

	public static void decodeYUV(int[] out, byte[] fg, int width, int height)
			throws NullPointerException, IllegalArgumentException
	{
		final int sz = width * height;
		if (out == null)
			throw new NullPointerException("buffer 'out' is null");
		if (out.length < sz)
			throw new IllegalArgumentException("buffer 'out' size " + out.length
					+ " < minimum " + sz);
		if (fg == null)
			throw new NullPointerException("buffer 'fg' is null");
		if (fg.length < sz)
			throw new IllegalArgumentException("buffer 'fg' size " + fg.length
					+ " < minimum " + sz * 3 / 2);
		int i, j;
		int Y, Cr = 0, Cb = 0;
		for (j = 0; j < height; j++)
		{
			int pixPtr = j * width;
			final int jDiv2 = j >> 1;
			for (i = 0; i < width; i++)
			{
				Y = fg[pixPtr];
				if (Y < 0)
					Y += 255;
				if ((i & 0x1) != 1)
				{
					final int cOff = sz + jDiv2 * width + (i >> 1) * 2;
					Cb = fg[cOff];
					if (Cb < 0)
						Cb += 127;
					else
						Cb -= 128;
					Cr = fg[cOff + 1];
					if (Cr < 0)
						Cr += 127;
					else
						Cr -= 128;
				}
				int R = Y + Cr + (Cr >> 2) + (Cr >> 3) + (Cr >> 5);
				if (R < 0)
					R = 0;
				else if (R > 255)
					R = 255;
				int G = Y - (Cb >> 2) + (Cb >> 4) + (Cb >> 5) - (Cr >> 1) + (Cr >> 3)
						+ (Cr >> 4) + (Cr >> 5);
				if (G < 0)
					G = 0;
				else if (G > 255)
					G = 255;
				int B = Y + Cb + (Cb >> 1) + (Cb >> 2) + (Cb >> 6);
				if (B < 0)
					B = 0;
				else if (B > 255)
					B = 255;
				out[pixPtr++] = 0xff000000 + (R << 16) + (G << 8) + B;
			}
		}
	}
}
