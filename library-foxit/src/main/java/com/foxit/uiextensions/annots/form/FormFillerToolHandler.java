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
package com.foxit.uiextensions.annots.form;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;

import com.foxit.sdk.PDFException;
import com.foxit.sdk.PDFViewCtrl;
import com.foxit.sdk.pdf.PDFDoc;
import com.foxit.sdk.pdf.PDFPage;
import com.foxit.sdk.pdf.Signature;
import com.foxit.sdk.pdf.annots.Annot;
import com.foxit.sdk.pdf.annots.Widget;
import com.foxit.sdk.pdf.interform.Control;
import com.foxit.sdk.pdf.interform.Field;
import com.foxit.sdk.pdf.interform.Form;
import com.foxit.uiextensions.ToolHandler;
import com.foxit.uiextensions.UIExtensionsManager;
import com.foxit.uiextensions.annots.common.EditAnnotEvent;
import com.foxit.uiextensions.annots.common.EditAnnotTask;
import com.foxit.uiextensions.annots.form.undo.FormFillerAddUndoItem;
import com.foxit.uiextensions.controls.propertybar.PropertyBar;
import com.foxit.uiextensions.utils.AppDisplay;
import com.foxit.uiextensions.utils.AppDmUtil;
import com.foxit.uiextensions.utils.AppUtil;
import com.foxit.uiextensions.utils.Event;


public class FormFillerToolHandler implements ToolHandler {
    private PDFViewCtrl mPdfViewCtrl;
    private FormFillerModule mFormFillerModule;
    private UIExtensionsManager mUiExtensionsManager;
    private Context mContext;
    private Form mForm;

    private PointF mStartPoint = new PointF(0, 0);
    private PointF mStopPoint = new PointF(0, 0);
    private PointF mDownPoint = new PointF(0, 0);// whether moving point

    private RectF mDrawRect = new RectF(0, 0, 0, 0);
    private Paint mPaint;

    private float mThickness;
    private float mCtlPtLineWidth = 2;
    private float mCtlPtRadius = 5;
    private boolean mTouchCaptured = false;
    private int mLastPageIndex = -1;
    private int mControlPtEx = 5;// Refresh the scope expansion width

    private int mCreateMode;

    public FormFillerToolHandler(Context context, PDFViewCtrl pdfViewCtrl, FormFillerModule module) {
        mContext = context;
        mPdfViewCtrl = pdfViewCtrl;
        mUiExtensionsManager = (UIExtensionsManager) pdfViewCtrl.getUIExtensionsManager();
        mFormFillerModule = module;

        mPaint = new Paint();
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        // FormHighlightColor
        mPaint.setColor(Color.parseColor("#0066cc"));

        mThickness = 2.0f;
    }


    @Override
    public String getType() {
        return ToolHandler.TH_TYPE_FORMFILLER;
    }

    @Override
    public void onActivate() {
        mTouchCaptured = false;
        mLastPageIndex = -1;
        mCtlPtRadius = 5;
        mCtlPtRadius = AppDisplay.getInstance(mContext).dp2px(mCtlPtRadius);
    }

    @Override
    public void onDeactivate() {
    }

    private Rect mTempRectInTouch = new Rect(0, 0, 0, 0);
    private Rect mInvalidateRect = new Rect(0, 0, 0, 0);

    @Override
    public boolean onTouchEvent(int pageIndex, MotionEvent motionEvent) {
        PointF disPoint = new PointF(motionEvent.getX(), motionEvent.getY());
        PointF pvPoint = new PointF();
        mPdfViewCtrl.convertDisplayViewPtToPageViewPt(disPoint, pvPoint, pageIndex);
        float x = pvPoint.x;
        float y = pvPoint.y;
        int action = motionEvent.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!mTouchCaptured && mLastPageIndex == -1 || mLastPageIndex == pageIndex) {
                    mTouchCaptured = true;
                    mStartPoint.x = x;
                    mStartPoint.y = y;
                    mStopPoint.x = x;
                    mStopPoint.y = y;
                    mDownPoint.set(x, y);
                    mTempRectInTouch.setEmpty();
                    if (mLastPageIndex == -1) {
                        mLastPageIndex = pageIndex;
                    }
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mTouchCaptured || mLastPageIndex != pageIndex)
                    break;
                if (!mDownPoint.equals(x, y)) {
                    mStopPoint.x = x;
                    mStopPoint.y = y;
                    float thickness = thicknessOnPageView(pageIndex, mThickness);
                    float deltaXY = thickness / 2 + mCtlPtLineWidth + mCtlPtRadius * 2 + 2;// Judging border value
                    float line_k = (y - mStartPoint.y) / (x - mStartPoint.x);
                    float line_b = mStartPoint.y - line_k * mStartPoint.x;
                    if (y <= deltaXY && line_k != 0) {
                        // whether created annot beyond a PDF page(pageView)
                        mStopPoint.y = deltaXY;
                        mStopPoint.x = (mStopPoint.y - line_b) / line_k;
                    } else if (y >= (mPdfViewCtrl.getPageViewHeight(pageIndex) - deltaXY) && line_k != 0) {
                        mStopPoint.y = (mPdfViewCtrl.getPageViewHeight(pageIndex) - deltaXY);
                        mStopPoint.x = (mStopPoint.y - line_b) / line_k;
                    }
                    if (mStopPoint.x <= deltaXY) {
                        mStopPoint.x = deltaXY;
                    } else if (mStopPoint.x >= mPdfViewCtrl.getPageViewWidth(pageIndex) - deltaXY) {
                        mStopPoint.x = mPdfViewCtrl.getPageViewWidth(pageIndex) - deltaXY;
                    }

                    getDrawRect(mStartPoint.x, mStartPoint.y, mStopPoint.x, mStopPoint.y);

                    mInvalidateRect.set((int) mDrawRect.left, (int) mDrawRect.top, (int) mDrawRect.right, (int) mDrawRect.bottom);
                    mInvalidateRect.inset((int) (-mThickness * 12f - mControlPtEx), (int) (-mThickness * 12f - mControlPtEx));
                    if (!mTempRectInTouch.isEmpty()) {
                        mInvalidateRect.union(mTempRectInTouch);
                    }
                    mTempRectInTouch.set(mInvalidateRect);
                    RectF _rect = AppDmUtil.rectToRectF(mInvalidateRect);
                    mPdfViewCtrl.convertPageViewRectToDisplayViewRect(_rect, _rect, pageIndex);
                    mPdfViewCtrl.invalidate(AppDmUtil.rectFToRect(_rect));
                    mDownPoint.set(x, y);
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mTouchCaptured || mLastPageIndex != pageIndex)
                    break;
                if (!mStartPoint.equals(mStopPoint.x, mStopPoint.y)) {
                    createFormField();
                } else {
                    mStartPoint.set(0, 0);
                    mStopPoint.set(0, 0);
                    mDrawRect.setEmpty();
                    mDownPoint.set(0, 0);

                    mTouchCaptured = false;
                    mLastPageIndex = -1;
                    mDownPoint.set(0, 0);
                }
                return true;
            default:
                return true;

        }
        return false;
    }

    private RectF mPageViewThickness = new RectF(0, 0, 0, 0);

    private float thicknessOnPageView(int pageIndex, float thickness) {
        mPageViewThickness.set(0, 0, thickness, thickness);
        mPdfViewCtrl.convertPdfRectToPageViewRect(mPageViewThickness, mPageViewThickness, pageIndex);
        return Math.abs(mPageViewThickness.width());
    }

    private void getDrawRect(float x1, float y1, float x2, float y2) {
        float minx = Math.min(x1, x2);
        float miny = Math.min(y1, y2);
        float maxx = Math.max(x1, x2);
        float maxy = Math.max(y1, y2);

        mDrawRect.left = minx;
        mDrawRect.top = miny;
        mDrawRect.right = maxx;
        mDrawRect.bottom = maxy;
    }

    @Override
    public boolean onLongPress(int pageIndex, MotionEvent motionEvent) {
        return false;
    }

    private RectF mTempRect = new RectF(0, 0, 0, 0);

    private RectF getBBox(int pageIndex) {
        RectF bboxRect = new RectF();
        mTempRect.set(mDrawRect);

        mTempRect.inset(-thicknessOnPageView(pageIndex, mThickness) / 2f, -thicknessOnPageView(pageIndex, mThickness) / 2f);

        mPdfViewCtrl.convertPageViewRectToPdfRect(mTempRect, mTempRect, pageIndex);
        bboxRect.left = mTempRect.left;
        bboxRect.right = mTempRect.right;
        bboxRect.top = mTempRect.top;
        bboxRect.bottom = mTempRect.bottom;

        return bboxRect;
    }

    private boolean mHasForm = false;

    private void createFormField() {
        if (mPdfViewCtrl.isPageVisible(mLastPageIndex)) {
            RectF bboxRect = getBBox(mLastPageIndex);
            try {
                final PDFDoc doc = mPdfViewCtrl.getDoc();
                mHasForm = doc.hasForm();
                if (mForm == null) {
                    mForm = new Form(doc);
                }
                Widget widget = null;
                final PDFPage page = doc.getPage(mLastPageIndex);
                int fieldType = Field.e_TypeUnknown;
                String fieldName = AppDmUtil.randomUUID(null);
                final FormFillerAddUndoItem undoItem = new FormFillerAddUndoItem(mPdfViewCtrl);

                if (FormFillerModule.CREATE_SIGNATURE_FILED_MODE != mCreateMode) {
                    if (FormFillerModule.CREATE_TEXT_FILED_MODE == mCreateMode) {
                        fieldType = Field.e_TypeTextField;
                    } else if (FormFillerModule.CREATE_CHECKBOX_FILED_MODE == mCreateMode) {
                        fieldType = Field.e_TypeCheckBox;
                    }
                    final Control control = mForm.addControl(page, fieldName, fieldType, AppUtil.toFxRectF(bboxRect));
                    undoItem.mNM = FormFillerModule.ID_TAG + "_" + fieldName;
                    undoItem.mFieldName = fieldName;
                    undoItem.mFieldType = fieldType;

                    widget = control.getWidget();
                } else {
                    final Signature signature = page.addSignature(AppUtil.toFxRectF(bboxRect));
                    undoItem.mNM = FormFillerModule.ID_TAG + "_" + AppDmUtil.randomUUID(null);
                    undoItem.mFieldType = Field.e_TypeSignature;

                    widget = signature.getControl(0).getWidget();
                }
                undoItem.mPageIndex = mLastPageIndex;
                undoItem.mFlags = Annot.e_FlagPrint;
                undoItem.mBBox = new RectF(bboxRect);
                undoItem.mModifiedDate = AppDmUtil.currentDateToDocumentDate();
                undoItem.mRotation = (page.getRotation() + mPdfViewCtrl.getViewRotation()) % 4;

                FormFillerEvent event = new FormFillerEvent(EditAnnotEvent.EVENTTYPE_ADD, undoItem, widget, mPdfViewCtrl);
                final Widget finalWidget = widget;
                EditAnnotTask task = new EditAnnotTask(event, new Event.Callback() {
                    @Override
                    public void result(Event event, boolean success) {
                        if (success) {
                            mUiExtensionsManager.getDocumentManager().onAnnotAdded(page, finalWidget);
                            mUiExtensionsManager.getDocumentManager().addUndoItem(undoItem);

                            if (mPdfViewCtrl.isPageVisible(mLastPageIndex)) {
                                try {
                                    RectF viewRect = AppUtil.toRectF(finalWidget.getRect());
                                    mPdfViewCtrl.convertPdfRectToPageViewRect(viewRect, viewRect, mLastPageIndex);
                                    Rect rect = new Rect();
                                    viewRect.roundOut(rect);
                                    rect.inset(-10, -10);
                                    mPdfViewCtrl.refresh(mLastPageIndex, rect);
                                } catch (PDFException e) {
                                    e.printStackTrace();
                                }
                                mTouchCaptured = false;
                                mLastPageIndex = -1;
                                mDownPoint.set(0, 0);
                            }

                            if (!mHasForm){
                                mHasForm = true;
                                mFormFillerModule.initForm(doc);
                            }
                        }
                    }
                });
                mPdfViewCtrl.addTask(task);
            } catch (PDFException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onSingleTapConfirmed(int pageIndex, MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean isContinueAddAnnot() {
        return false;
    }

    @Override
    public void setContinueAddAnnot(boolean continueAddAnnot) {
    }

    @Override
    public void onDraw(int pageIndex, Canvas canvas) {
        if (mLastPageIndex == pageIndex) {
            canvas.save();
            setPaint(pageIndex);

            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setAlpha(200);
            canvas.drawRect(mDrawRect, mPaint);

            mDrawRect.inset(thicknessOnPageView(pageIndex, mThickness)/ 2,thicknessOnPageView(pageIndex, mThickness)/ 2);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setAlpha(32);
            canvas.drawRect(mDrawRect, mPaint);
            canvas.restore();
        }
    }

    private void setPaint(int pageIndex) {
        mPaint.setAntiAlias(true);
        PointF tranPt = new PointF(thicknessOnPageView(pageIndex, mThickness), thicknessOnPageView(pageIndex, mThickness));
        mPaint.setStrokeWidth(tranPt.x);
    }

    protected void setCreateMode(int mode) {
        this.mCreateMode = mode;
    }

    protected boolean onKeyBack() {
        if (mUiExtensionsManager.getCurrentToolHandler() == this) {
            mUiExtensionsManager.setCurrentToolHandler(null);
            return true;
        }
        return false;
    }

    protected void reset() {
        mHasForm = false;
        mForm = null;
    }

}
