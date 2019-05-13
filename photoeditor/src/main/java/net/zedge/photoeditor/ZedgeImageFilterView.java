package net.zedge.photoeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.effect.Effect;
import android.media.effect.EffectContext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;

import ja.burhanrashid52.photoeditor.BitmapUtil;
import ja.burhanrashid52.photoeditor.CustomEffect;
import ja.burhanrashid52.photoeditor.GLToolbox;
import ja.burhanrashid52.photoeditor.ImageFilterView;
import ja.burhanrashid52.photoeditor.PhotoFilter;

public class ZedgeImageFilterView extends ImageFilterView {

    private static final String TAG = "ZedgeImageFilterView";
    private boolean mShouldReloadTexture = false;
    private boolean mShouldReloadEffect = false;
    private Effect mNewEffect = null;
    private final Object mDrawLock = new Object();

    public interface RenderJobSuccessCallback {
        void onRenderJobSuccess(String jobName, Bitmap bitmap);
    }

    public interface RenderJobFailureCallback {
        void onRenderJobFailure(String jobName, Throwable t);
    }

    public static class RenderJob {
        String jobId;
        Effect effect;
        float scale;
        RenderJobSuccessCallback successCallback;
        RenderJobFailureCallback failureCallback;
        public RenderJob(String jobId,
                         Effect effect,
                         float scale,
                         RenderJobSuccessCallback successCallback,
                         RenderJobFailureCallback failureCallback) {
            this.jobId = jobId;
            this.effect = effect;
            this.scale = scale;
            this.successCallback = successCallback;
            this.failureCallback = failureCallback;
        }
    }

    private List<RenderJob> mRenderJobs = Collections.synchronizedList(new ArrayList<RenderJob>());

    public ZedgeImageFilterView(Context context) {
        super(context);
    }

    public ZedgeImageFilterView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void setSourceBitmap(Bitmap sourceBitmap) {
        synchronized (mDrawLock) {
            if (sourceBitmap == mSourceBitmap) {
                return;
            }
            mSourceBitmap = sourceBitmap;
            mShouldReloadTexture = true;
        }
    }

    private void prepareToDraw() {
        if (mEffectContext == null) {
            mEffectContext = EffectContext.createWithCurrentGlContext();
        }
        if (!mTexRenderer.isInitialized()) {
            mTexRenderer.init();
            createTextures();
            reloadTextures();
        }
        if (mShouldReloadTexture) {
            mShouldReloadTexture = false;
            reloadTextures();
        }
    }

    private void drawJobFrame(RenderJob renderJob) {
        int viewPortWidth = Math.round(mImageWidth * renderJob.scale);
        int viewPortHeight = Math.round(mImageHeight * renderJob.scale);
        applyEffect(renderJob.effect, viewPortWidth, viewPortHeight);
        try {
            Bitmap bitmap = BitmapUtil.createBitmapFromGlFrameBuffer(
                    0, 0, viewPortWidth, viewPortHeight);
            renderJob.successCallback.onRenderJobSuccess(renderJob.jobId, bitmap);
        } catch (Throwable t) {
            renderJob.failureCallback.onRenderJobFailure(renderJob.jobId, t);
        }
        renderJob.effect.release();
    }

    private void drawNormalFrame(GL10 gl) {
        if (mShouldReloadEffect) {
            mShouldReloadEffect = false;
            initEffect();
        }
        if (mEffect != null) {
            applyEffect(mEffect, mImageWidth, mImageHeight);
        }
        renderResult();
        if (isSaveImage) {
            isSaveImage = false;
            try {
                final Bitmap mFilterBitmap = BitmapUtil.createBitmapFromGLSurface(this, gl);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (mOnSaveBitmap != null) {
                            mOnSaveBitmap.onBitmapReady(mFilterBitmap);
                        }
                    }
                });
            } catch (final Throwable e) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (mOnSaveBitmap != null) {
                            mOnSaveBitmap.onFailure(e);
                        }
                    }
                });
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        synchronized (mDrawLock) {
            if (mSourceBitmap == null) {
                return;
            }
            prepareToDraw();
            if (!mRenderJobs.isEmpty()) {
                drawJobFrame(mRenderJobs.remove(0));
                drawNormalFrame(gl);
                if (!mRenderJobs.isEmpty()) {
                    // Trigger another render iteration to handle the next job.
                    requestRender();
                }
            } else {
                drawNormalFrame(gl);
            }
        }
    }

    @Override
    protected void renderResult() {
        if (mEffect != null) {
            // render the result of applyEffect()
            mTexRenderer.renderTexture(mTextures[1]);
        } else {
            // if no effect is chosen, just render the original bitmap
            mTexRenderer.renderTexture(mTextures[0]);
        }
    }

    @Override
    protected void setFilterEffect(PhotoFilter effect) {
        synchronized (mDrawLock) {
            if (effect == PhotoFilter.NONE) {
                removeEffect();
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Override
    protected void setFilterEffect(CustomEffect customEffect) {
        synchronized (mDrawLock) {
            throw new UnsupportedOperationException();
        }
    }

    public void setFilterEffect(Effect effect) {
        synchronized (mDrawLock) {
            mNewEffect = effect;
            mShouldReloadEffect = true;
            requestRender();
        }
    }

    void submitRenderJob(RenderJob renderJob) {
        synchronized (mDrawLock) {
            mRenderJobs.add(renderJob);
            requestRender();
        }
    }

    void cancelRenderJob(String jobId) {
        synchronized (mDrawLock) {
            for (RenderJob renderJob : mRenderJobs) {
                if (renderJob.jobId.equals(jobId)) {
                    mRenderJobs.remove(renderJob);
                }
            }
        }
    }

    protected void removeEffect() {
        synchronized (mDrawLock) {
            mCurrentEffect = PhotoFilter.NONE;
            mCustomEffect = null;
            mNewEffect = null;
            mShouldReloadEffect = true;
            requestRender();
        }
    }

    private void reloadTextures() {
        // Load input bitmap
        if (mSourceBitmap != null) {
            mImageWidth = mSourceBitmap.getWidth();
            mImageHeight = mSourceBitmap.getHeight();
            mTexRenderer.updateTextureSize(mImageWidth, mImageHeight);

            // Upload to texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures[0]);
            GLToolbox.checkGlError("glBindTexture");
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mSourceBitmap, 0);
            GLToolbox.checkGlError("texImage2D");

            // Set texture parameters
            GLToolbox.initTexParams();
        }
    }

    private void createTextures() {
        GLES20.glGenTextures(2, mTextures, 0);
    }

    private void initEffect() {
        if (mEffect != null) {
            mEffect.release();
            mEffect = null;
        }
        if (mNewEffect != null) {
            mEffect = mNewEffect;
            mNewEffect = null;
        }
    }

    private void applyEffect(Effect effect, int viewPortWidth, int viewPortHeight) {
        effect.apply(mTextures[0], viewPortWidth, viewPortHeight, mTextures[1]);
    }

}
