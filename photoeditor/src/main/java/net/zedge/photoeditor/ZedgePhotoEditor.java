package net.zedge.photoeditor;

import android.content.Context;
import android.media.effect.Effect;

import ja.burhanrashid52.photoeditor.DefaultLogger;
import ja.burhanrashid52.photoeditor.Logger;
import ja.burhanrashid52.photoeditor.PhotoEditor;

public class ZedgePhotoEditor extends PhotoEditor {

    private ZedgePhotoEditorView mZedgePhotoEditorView;
    private Logger mLogger;

    private ZedgePhotoEditor(Builder builder) {
        super(builder);
        mZedgePhotoEditorView = builder.zedgePhotoEditorView;
        mLogger = builder.mLogger;
        brushDrawingView.setLogger(mLogger);
    }

    public void setFilterEffect(Effect effect) {
        mZedgePhotoEditorView.setFilterEffect(effect);
    }

    public void createFilterPreview(ZedgeImageFilterView.RenderJob renderJob) {
        mZedgePhotoEditorView.submitRenderJob(renderJob);
    }

    public void cancelFilterPreview(String jobId) {
        mZedgePhotoEditorView.cancelRenderJob(jobId);
    }

    public void removeFilter() {
        mZedgePhotoEditorView.removeFilter();
    }

    public static class Builder extends PhotoEditor.Builder {
        ZedgePhotoEditorView zedgePhotoEditorView;
        Logger mLogger = new DefaultLogger();

        public Builder(Context context, ZedgePhotoEditorView photoEditorView) {
            super(context, photoEditorView);
            zedgePhotoEditorView = photoEditorView;
        }

        public Builder setLogger(Logger logger) {
            mLogger = logger;
            return this;
        }

        @Override
        public ZedgePhotoEditor build() {
            return new ZedgePhotoEditor(this);
        }
    }
}