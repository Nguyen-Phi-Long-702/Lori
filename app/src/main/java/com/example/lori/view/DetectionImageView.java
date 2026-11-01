package com.example.lori.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.graphics.ColorUtils;

import com.example.lori.data.remote.dto.AiImageResponse;
import com.google.android.material.color.MaterialColors;

import java.util.Collections;
import java.util.List;

//Hiện ảnh và vẽ khung quanh các vật nhận diện được. Khung đang chọn đậm và có nhãn tên từ; các khung còn lại mờ
//Dùng scaleType fitCenter (không đặt padding) để khung khớp ảnh
public class DetectionImageView extends AppCompatImageView {

    private static final float SELECTED_STROKE_DP = 3f;
    private static final float OTHER_STROKE_DP = 1.5f;
    private static final float LABEL_TEXT_SP = 12f;
    private static final float LABEL_PADDING_DP = 4f;
    private static final int OTHER_BOX_ALPHA = 160;

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF imageRect = new RectF();
    private final float density;
    private final int primaryColor;

    private List<AiImageResponse.ImageObject> objects = Collections.emptyList();
    private int selectedIndex;

    public DetectionImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        primaryColor = MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary);

        boxPaint.setStyle(Paint.Style.STROKE);
        labelBgPaint.setColor(primaryColor);
        labelTextPaint.setColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnPrimary));
        labelTextPaint.setFakeBoldText(true);
        labelTextPaint.setTextSize(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, LABEL_TEXT_SP, getResources().getDisplayMetrics()));
    }

    //Đặt danh sách vật và vật đang chọn rồi vẽ lại
    public void setObjects(List<AiImageResponse.ImageObject> objects, int selectedIndex) {
        this.objects = objects;
        this.selectedIndex = selectedIndex;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        Drawable drawable = getDrawable();
        if (drawable == null || objects.isEmpty()) {
            return;
        }
        //Hình chữ nhật mà ảnh thực sự chiếm trong view sau khi fitCenter
        imageRect.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        getImageMatrix().mapRect(imageRect);

        for (int i = 0; i < objects.size(); i++) {
            if (i != selectedIndex) {
                drawBox(canvas, objects.get(i), false);
            }
        }
        //Vẽ khung đang chọn sau cùng để nằm trên các khung khác
        if (selectedIndex >= 0 && selectedIndex < objects.size()) {
            drawBox(canvas, objects.get(selectedIndex), true);
        }
    }

    private void drawBox(Canvas canvas, AiImageResponse.ImageObject object, boolean selected) {
        if (object.box == null) {
            return;
        }
        float left = imageRect.left + (float) object.box.x * imageRect.width();
        float top = imageRect.top + (float) object.box.y * imageRect.height();
        float right = left + (float) object.box.width * imageRect.width();
        float bottom = top + (float) object.box.height * imageRect.height();

        boxPaint.setStrokeWidth((selected ? SELECTED_STROKE_DP : OTHER_STROKE_DP) * density);
        boxPaint.setColor(selected ? primaryColor : ColorUtils.setAlphaComponent(primaryColor, OTHER_BOX_ALPHA));
        canvas.drawRect(left, top, right, bottom, boxPaint);

        if (selected && object.word != null) {
            drawLabel(canvas, object.word, left, top);
        }
    }

    //Nhãn tên từ nằm ngay trên góc trên-trái của khung; khung sát mép trên ảnh thì đặt nhãn vào trong khung
    private void drawLabel(Canvas canvas, String text, float left, float top) {
        float padding = LABEL_PADDING_DP * density;
        float textHeight = labelTextPaint.descent() - labelTextPaint.ascent();
        float labelHeight = textHeight + 2 * padding;
        float labelTop = top - labelHeight < imageRect.top ? top : top - labelHeight;
        float labelWidth = labelTextPaint.measureText(text) + 2 * padding;

        canvas.drawRect(left, labelTop, left + labelWidth, labelTop + labelHeight, labelBgPaint);
        canvas.drawText(text, left + padding, labelTop + padding - labelTextPaint.ascent(), labelTextPaint);
    }
}