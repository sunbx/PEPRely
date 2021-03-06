/**
 * Copyright (C) 2003-2019, Foxit Software Inc..
 * All Rights Reserved.
 * <p>
 * http://www.foxitsoftware.com
 * <p>
 * The following code is copyrighted and is the proprietary of Foxit Software Inc.. It is not allowed to
 * distribute any parts of Foxit PDF SDK to third party or public without permission unless an agreement
 * is signed between Foxit Software Inc. and customers to explicitly grant customers permissions.
 * Review legal.txt for additional license and legal information.
 */
package com.foxit.uiextensions.annots.polygon;

import android.graphics.RectF;

import com.foxit.sdk.PDFException;
import com.foxit.sdk.PDFViewCtrl;
import com.foxit.sdk.common.fxcrt.PointFArray;
import com.foxit.sdk.pdf.PDFPage;
import com.foxit.sdk.pdf.annots.Annot;
import com.foxit.sdk.pdf.annots.BorderInfo;
import com.foxit.sdk.pdf.annots.Polygon;
import com.foxit.uiextensions.UIExtensionsManager;
import com.foxit.uiextensions.annots.AnnotUndoItem;
import com.foxit.uiextensions.annots.common.EditAnnotEvent;
import com.foxit.uiextensions.annots.common.EditAnnotTask;
import com.foxit.uiextensions.utils.AppAnnotUtil;
import com.foxit.uiextensions.utils.AppDmUtil;
import com.foxit.uiextensions.utils.AppUtil;
import com.foxit.uiextensions.utils.Event;

public abstract class PolygonUndoItem extends AnnotUndoItem {
    PointFArray mVertexes;
    float mIntensity; // for cloud
}

class PolygonAddUndoItem extends PolygonUndoItem {

    public PolygonAddUndoItem(PDFViewCtrl pdfViewCtrl) {
        mPdfViewCtrl = pdfViewCtrl;
    }

    @Override
    public boolean undo() {
        PolygonDeleteUndoItem undoItem = new PolygonDeleteUndoItem(mPdfViewCtrl);
        undoItem.mNM = mNM;
        undoItem.mPageIndex = mPageIndex;

        try {
            final PDFPage page = mPdfViewCtrl.getDoc().getPage(mPageIndex);
            final Annot annot = ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().getAnnot(page, mNM);
            if (!(annot instanceof Polygon)) {
                return false;
            }

            final RectF annotRectF = AppUtil.toRectF(annot.getRect());

            if (annot == ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().getCurrentAnnot()) {
                ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().setCurrentAnnot(null, false);
            }

            ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().onAnnotWillDelete(page, annot);

            PolygonEvent deleteEvent = new PolygonEvent(EditAnnotEvent.EVENTTYPE_DELETE, undoItem, (Polygon) annot, mPdfViewCtrl);
            EditAnnotTask task = new EditAnnotTask(deleteEvent, new Event.Callback() {
                @Override
                public void result(Event event, boolean success) {
                    if (success) {
                        ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().onAnnotDeleted(page, annot);
                        if (mPdfViewCtrl.isPageVisible(mPageIndex)) {
                            RectF deviceRectF = new RectF();
                            mPdfViewCtrl.convertPdfRectToPageViewRect(annotRectF, deviceRectF, mPageIndex);
                            mPdfViewCtrl.refresh(mPageIndex, AppDmUtil.rectFToRect(deviceRectF));
                        }
                    }
                }
            });
            mPdfViewCtrl.addTask(task);
            return true;
        } catch (PDFException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean redo() {
        try {
            final PDFPage page = mPdfViewCtrl.getDoc().getPage(mPageIndex);
            final Annot annot = AppAnnotUtil.createAnnot(page.addAnnot(Annot.e_Polygon, new com.foxit.sdk.common.fxcrt.RectF(0, 0, 0, 0)), Annot.e_Polygon);

            PolygonEvent addEvent = new PolygonEvent(EditAnnotEvent.EVENTTYPE_ADD, this, (Polygon) annot, mPdfViewCtrl);
            EditAnnotTask task = new EditAnnotTask(addEvent, new Event.Callback() {
                @Override
                public void result(Event event, boolean success) {
                    if (success) {
                        ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().onAnnotAdded(page, annot);
                        if (mPdfViewCtrl.isPageVisible(mPageIndex)) {
                            try {
                                RectF annotRect = AppUtil.toRectF(annot.getRect());
                                mPdfViewCtrl.convertPdfRectToPageViewRect(annotRect, annotRect, mPageIndex);
                                mPdfViewCtrl.refresh(mPageIndex, AppDmUtil.rectFToRect(annotRect));
                            } catch (PDFException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
            mPdfViewCtrl.addTask(task);
            return true;
        } catch (PDFException e) {
            e.printStackTrace();
        }

        return false;
    }
}

class PolygonModifyUndoItem extends PolygonUndoItem {
    public int 		mUndoColor;
    public float 	mUndoOpacity;
    public float    mUndoLineWidth;
    public RectF    mUndoBbox;
    public String   mUndoContent;
    public PointFArray   mUndoVertexes;


    public int		mRedoColor;
    public float	mRedoOpacity;
    public float    mRedoLineWidth;
    public RectF    mRedoBbox;
    public String   mRedoContent;
    public PointFArray mRedoVertexes;

    public PolygonModifyUndoItem(PDFViewCtrl pdfViewCtrl) {
        mPdfViewCtrl = pdfViewCtrl;
    }

    @Override
    public boolean undo() {
        return modifyAnnot(mUndoColor, mUndoOpacity, mUndoLineWidth, mUndoBbox, mUndoVertexes, mUndoContent);
    }

    @Override
    public boolean redo() {
        return modifyAnnot(mRedoColor, mRedoOpacity, mRedoLineWidth, mRedoBbox, mRedoVertexes, mRedoContent);
    }

    private boolean modifyAnnot(int color, float opacity, float lineWidth, RectF bbox, PointFArray vertexes, String content) {
        try {
            final PDFPage page = mPdfViewCtrl.getDoc().getPage(mPageIndex);
            final Annot annot = ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().getAnnot(page, mNM);
            if (!(annot instanceof Polygon)) {
                return false;
            }

            final RectF oldBbox = AppUtil.toRectF(annot.getRect());
            mColor = color;
            mOpacity = opacity;
            mBBox = new RectF(bbox);
            mModifiedDate = AppDmUtil.currentDateToDocumentDate();
            mLineWidth = lineWidth;
            mContents = content;
            mVertexes = vertexes;

            PolygonEvent modifyEvent = new PolygonEvent(EditAnnotEvent.EVENTTYPE_MODIFY, this, (Polygon) annot, mPdfViewCtrl);
            EditAnnotTask task = new EditAnnotTask(modifyEvent, new Event.Callback() {
                @Override
                public void result(Event event, boolean success) {
                    if (success) {
                        if (annot == ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().getCurrentAnnot()) {
                            ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().setCurrentAnnot(null);
                        }

                        ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().onAnnotModified(page, annot);
                        if (mPdfViewCtrl.isPageVisible(mPageIndex)) {
                            try {
                                RectF annotRect = AppUtil.toRectF(annot.getRect());
                                mPdfViewCtrl.convertPdfRectToPageViewRect(annotRect, annotRect, mPageIndex);
                                mPdfViewCtrl.refresh(mPageIndex, AppDmUtil.rectFToRect(annotRect));

                                mPdfViewCtrl.convertPdfRectToPageViewRect(oldBbox, oldBbox, mPageIndex);
                                mPdfViewCtrl.refresh(mPageIndex, AppDmUtil.rectFToRect(oldBbox));
                            } catch (PDFException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
            mPdfViewCtrl.addTask(task);
            return true;
        } catch (PDFException e) {
            e.printStackTrace();
        }
        return false;
    }
}

class PolygonDeleteUndoItem extends PolygonUndoItem {

    public PolygonDeleteUndoItem(PDFViewCtrl pdfViewCtrl) {
        mPdfViewCtrl = pdfViewCtrl;
    }

    @Override
    public boolean undo(Event.Callback callback) {
        PolygonAddUndoItem undoItem = new PolygonAddUndoItem(mPdfViewCtrl);
        undoItem.mPageIndex = mPageIndex;
        undoItem.mNM = mNM;
        undoItem.mAuthor = mAuthor;
        undoItem.mFlags = mFlags;
        undoItem.mSubject = mSubject;
        undoItem.mCreationDate = mCreationDate;
        undoItem.mModifiedDate = mModifiedDate;
        undoItem.mBBox = new RectF(mBBox);
        undoItem.mColor = mColor;
        undoItem.mOpacity = mOpacity;
        undoItem.mLineWidth = mLineWidth;
        undoItem.mBorderStyle = mBorderStyle;
        undoItem.mContents = mContents;
        undoItem.mVertexes = mVertexes;
        if (mBorderStyle == BorderInfo.e_Cloudy) {
            undoItem.mIntensity = mIntensity;
        }

        try {
            final PDFPage page = mPdfViewCtrl.getDoc().getPage(mPageIndex);
            final Polygon annot = (Polygon) AppAnnotUtil.createAnnot(page.addAnnot(Annot.e_Polygon, AppUtil.toFxRectF(mBBox)), Annot.e_Polygon);
            PolygonEvent addEvent = new PolygonEvent(EditAnnotEvent.EVENTTYPE_ADD, undoItem, annot, mPdfViewCtrl);
            if (((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().isMultipleSelectAnnots()) {
                if (callback != null) {
                    callback.result(addEvent, true);
                }
                return true;
            }
            EditAnnotTask task = new EditAnnotTask(addEvent, new Event.Callback() {
                @Override
                public void result(Event event, boolean success) {
                    if (success) {
                        ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().onAnnotAdded(page, annot);
                        if (mPdfViewCtrl.isPageVisible(mPageIndex)) {
                            try {
                                RectF annotRectF = AppUtil.toRectF(annot.getRect());
                                mPdfViewCtrl.convertPdfRectToPageViewRect(annotRectF, annotRectF, mPageIndex);
                                mPdfViewCtrl.refresh(mPageIndex, AppDmUtil.rectFToRect(annotRectF));
                            } catch (PDFException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
            mPdfViewCtrl.addTask(task);
            return true;
        } catch (PDFException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean redo(Event.Callback callback) {
        try {
            final PDFPage page = mPdfViewCtrl.getDoc().getPage(mPageIndex);
            final Annot annot = ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().getAnnot(page, mNM);
            if (!(annot instanceof Polygon)) {
                return false;
            }

            if (annot == ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().getCurrentAnnot()) {
                ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().setCurrentAnnot(null, false);
            }

            ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().onAnnotWillDelete(page, annot);
            final RectF annotRectF = AppUtil.toRectF(annot.getRect());
            PolygonEvent deleteEvent = new PolygonEvent(EditAnnotEvent.EVENTTYPE_DELETE, this, (Polygon) annot, mPdfViewCtrl);
            if (((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().isMultipleSelectAnnots()) {
                if (callback != null) {
                    callback.result(deleteEvent, true);
                }
                return true;
            }
            EditAnnotTask task = new EditAnnotTask(deleteEvent, new Event.Callback() {
                @Override
                public void result(Event event, boolean success) {
                    if (success) {
                        ((UIExtensionsManager)mPdfViewCtrl.getUIExtensionsManager()).getDocumentManager().onAnnotDeleted(page, annot);
                        if (mPdfViewCtrl.isPageVisible(mPageIndex)) {
                            RectF deviceRectF = new RectF();
                            mPdfViewCtrl.convertPdfRectToPageViewRect(annotRectF, deviceRectF, mPageIndex);
                            mPdfViewCtrl.refresh(mPageIndex, AppDmUtil.rectFToRect(deviceRectF));
                        }
                    }
                }
            });
            mPdfViewCtrl.addTask(task);
            return true;
        } catch (PDFException e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean undo() {
        return undo(null);
    }

    @Override
    public boolean redo() {
        return redo(null);
    }
}