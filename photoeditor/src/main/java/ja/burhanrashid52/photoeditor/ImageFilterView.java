package ja.burhanrashid52.photoeditor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.effect.Effect;
import android.media.effect.EffectContext;
import android.media.effect.EffectFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.media.effect.EffectFactory.*;
import static ja.burhanrashid52.photoeditor.PhotoFilter.*;

/**
 * <p>
 * Filter Images using ImageFilterView
 * </p>
 *
 * @author <a href="https://github.com/burhanrashid52">Burhanuddin Rashid</a>
 * @version 0.1.2
 * @since 2/14/2018
 */
public class ImageFilterView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final String TAG = "ImageFilterView";
    private int[] mTextures = new int[2];
    private EffectContext mEffectContext;
    private Effect mEffect;
    private TextureRenderer mTexRenderer = new TextureRenderer();
    private int mImageWidth;
    private int mImageHeight;
    private boolean mShouldReloadTexture = false;
    private boolean mShouldReloadEffect = false;
    private PhotoFilter mCurrentEffect;
    private Bitmap mSourceBitmap;
    private CustomEffect mCustomEffect;
    private OnSaveBitmap mOnSaveBitmap;
    private boolean isSaveImage = false;

    public interface RenderJobCallback {
        void onRenderJobDone(String jobName, Bitmap bitmap);
    }

    private static class RenderJob {
        String jobId;
        CustomEffect customEffect;
        RenderJobCallback callback;
        public RenderJob(String jobId, CustomEffect customEffect, RenderJobCallback callback) {
            this.jobId = jobId;
            this.callback = callback;
            this.customEffect = customEffect;
        }
    }

    private List<RenderJob> mRenderJobs = Collections.synchronizedList(new ArrayList<RenderJob>());

    public ImageFilterView(Context context) {
        super(context);
        init();
    }

    public ImageFilterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        setFilterEffect(NONE);
    }

    void setSourceBitmap(Bitmap sourceBitmap) {
       /* if (mSourceBitmap != null && mSourceBitmap.sameAs(sourceBitmap)) {
            //mCurrentEffect = NONE;
        }*/
        if (sourceBitmap == mSourceBitmap) {
           return;
        }
        mSourceBitmap = sourceBitmap;
        mShouldReloadTexture = true;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {

    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        if (mTexRenderer != null) {
            mTexRenderer.updateViewSize(width, height);
        }
    }

    private void drawFrame(GL10 gl, @Nullable RenderJob renderJob) {
        //Only need to do this once
        if (mEffectContext == null) {
            mEffectContext = EffectContext.createWithCurrentGlContext();
        }
        if (!mTexRenderer.isInitialized()) {
            mTexRenderer.init();
            loadTextures();
        }
        if (mShouldReloadTexture) {
            mShouldReloadTexture = false;
            reloadTextures();
        }
        if (renderJob != null) {
            initEffect(renderJob.customEffect, null);
            applyEffect();
            renderJob.callback.onRenderJobDone(
                    renderJob.jobId, BitmapUtil.createBitmapFromGlFrameBuffer(0, 0, 200, 200));
            // Reload the original effect after the job is done.
            mShouldReloadEffect = true;
        } else {
            if (mCurrentEffect != NONE || mCustomEffect != null) {
                if (mShouldReloadEffect) {
                    mShouldReloadEffect = false;
                    //if an effect is chosen initialize it and apply it to the texture
                    initEffect(mCustomEffect, mCurrentEffect);
                }
                applyEffect();
            }
            renderResult();
            if (isSaveImage) {
                final Bitmap mFilterBitmap = BitmapUtil.createBitmapFromGLSurface(this, gl);
                isSaveImage = false;
                if (mOnSaveBitmap != null) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            mOnSaveBitmap.onBitmapReady(mFilterBitmap);
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (!mRenderJobs.isEmpty()) {
            RenderJob job = mRenderJobs.remove(0);
            drawFrame(gl, job);
            requestRender();
        } else {
            drawFrame(gl, null);
        }
    }

    void setFilterEffect(PhotoFilter effect) {
        mCurrentEffect = effect;
        mCustomEffect = null;
        mShouldReloadEffect = true;
        requestRender();
    }

    void setFilterEffect(CustomEffect customEffect) {
        mCustomEffect = customEffect;
        mShouldReloadEffect = true;
        requestRender();
    }

    void submitRenderJob(String jobId, CustomEffect effect, RenderJobCallback callback) {
        mRenderJobs.add(new RenderJob(jobId, effect, callback));
        requestRender();
    }

    void cancelRenderJob(String jobId) {
        synchronized (mRenderJobs) {
            for (RenderJob renderJob : mRenderJobs) {
                if (renderJob.jobId.equals(jobId)) {
                    mRenderJobs.remove(renderJob);
                }
            }
        }
    }

    void saveBitmap(OnSaveBitmap onSaveBitmap) {
        mOnSaveBitmap = onSaveBitmap;
        isSaveImage = true;
        requestRender();
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

    private void loadTextures() {
        // Generate textures
        GLES20.glGenTextures(2, mTextures, 0);
        reloadTextures();
    }

    private void initEffect(@Nullable CustomEffect customEffect, @Nullable PhotoFilter builtinEffect) {
        EffectFactory effectFactory = mEffectContext.getFactory();
        if (mEffect != null) {
            mEffect.release();
            mEffect = null;
        }
        if (customEffect != null) {
            mEffect = effectFactory.createEffect(customEffect.getEffectName());
            Map<String, Object> parameters = customEffect.getParameters();
            for (Map.Entry<String, Object> param : parameters.entrySet()) {
                mEffect.setParameter(param.getKey(), param.getValue());
            }
        } else if (builtinEffect != null) {
            // Initialize the correct effect based on the selected menu/action item
            switch (builtinEffect) {

                case AUTO_FIX:
                    mEffect = effectFactory.createEffect(EFFECT_AUTOFIX);
                    mEffect.setParameter("scale", 0.5f);
                    break;
                case BLACK_WHITE:
                    mEffect = effectFactory.createEffect(EFFECT_BLACKWHITE);
                    mEffect.setParameter("black", .1f);
                    mEffect.setParameter("white", .7f);
                    break;
                case BRIGHTNESS:
                    mEffect = effectFactory.createEffect(EFFECT_BRIGHTNESS);
                    mEffect.setParameter("brightness", 2.0f);
                    break;
                case CONTRAST:
                    mEffect = effectFactory.createEffect(EFFECT_CONTRAST);
                    mEffect.setParameter("contrast", 1.4f);
                    break;
                case CROSS_PROCESS:
                    mEffect = effectFactory.createEffect(EFFECT_CROSSPROCESS);
                    break;
                case DOCUMENTARY:
                    mEffect = effectFactory.createEffect(EFFECT_DOCUMENTARY);
                    break;
                case DUE_TONE:
                    mEffect = effectFactory.createEffect(EFFECT_DUOTONE);
                    mEffect.setParameter("first_color", Color.YELLOW);
                    mEffect.setParameter("second_color", Color.DKGRAY);
                    break;
                case FILL_LIGHT:
                    mEffect = effectFactory.createEffect(EFFECT_FILLLIGHT);
                    mEffect.setParameter("strength", .8f);
                    break;
                case FISH_EYE:
                    mEffect = effectFactory.createEffect(EFFECT_FISHEYE);
                    mEffect.setParameter("scale", .5f);
                    break;
                case FLIP_HORIZONTAL:
                    mEffect = effectFactory.createEffect(EFFECT_FLIP);
                    mEffect.setParameter("horizontal", true);
                    break;
                case FLIP_VERTICAL:
                    mEffect = effectFactory.createEffect(EFFECT_FLIP);
                    mEffect.setParameter("vertical", true);
                    break;
                case GRAIN:
                    mEffect = effectFactory.createEffect(EFFECT_GRAIN);
                    mEffect.setParameter("strength", 1.0f);
                    break;
                case GRAY_SCALE:
                    mEffect = effectFactory.createEffect(EFFECT_GRAYSCALE);
                    break;
                case LOMISH:
                    mEffect = effectFactory.createEffect(EFFECT_LOMOISH);
                    break;
                case NEGATIVE:
                    mEffect = effectFactory.createEffect(EFFECT_NEGATIVE);
                    break;
                case NONE:
                    break;
                case POSTERIZE:
                    mEffect = effectFactory.createEffect(EFFECT_POSTERIZE);
                    break;
                case ROTATE:
                    mEffect = effectFactory.createEffect(EFFECT_ROTATE);
                    mEffect.setParameter("angle", 180);
                    break;
                case SATURATE:
                    mEffect = effectFactory.createEffect(EFFECT_SATURATE);
                    mEffect.setParameter("scale", .5f);
                    break;
                case SEPIA:
                    mEffect = effectFactory.createEffect(EFFECT_SEPIA);
                    break;
                case SHARPEN:
                    mEffect = effectFactory.createEffect(EFFECT_SHARPEN);
                    break;
                case TEMPERATURE:
                    mEffect = effectFactory.createEffect(EFFECT_TEMPERATURE);
                    mEffect.setParameter("scale", .9f);
                    break;
                case TINT:
                    mEffect = effectFactory.createEffect(EFFECT_TINT);
                    mEffect.setParameter("tint", Color.MAGENTA);
                    break;
                case VIGNETTE:
                    mEffect = effectFactory.createEffect(EFFECT_VIGNETTE);
                    mEffect.setParameter("scale", .5f);
                    break;
            }
        }
    }

    private void applyEffect() {
        if (mEffect != null) {
            mEffect.apply(mTextures[0], mImageWidth, mImageHeight, mTextures[1]);
        }
    }

    private void renderResult() {
        if (mCurrentEffect != NONE || mCustomEffect != null) {
            // if no effect is chosen, just render the original bitmap
            mTexRenderer.renderTexture(mTextures[1]);
        } else {
            // render the result of applyEffect()
            mTexRenderer.renderTexture(mTextures[0]);
        }
    }
}
