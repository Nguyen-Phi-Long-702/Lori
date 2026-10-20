package com.example.lori.view;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.example.lori.R;

import java.util.Objects;

//Avatar hình tròn: hiện chữ cái đầu của tên, có ảnh (avatarUrl) thì tải bằng Glide rồi phủ lên trên
//Kích thước đặt ở layout; cỡ chữ tự tính theo kích thước view
public class AvatarView extends FrameLayout {

    //Cỡ chữ cái đầu so với chiều cao avatar
    private static final float INITIAL_TEXT_RATIO = 0.42f;

    private final TextView tvInitial;
    private final ImageView ivPhoto;

    //Tên và ảnh của lần bind gần nhất: bind lại đúng giá trị đó thì bỏ qua, tránh tải lại ảnh làm avatar nhấp nháy
    private boolean bound;
    private String boundName;
    private String boundUrl;

    public AvatarView(@NonNull Context context) {
        this(context, null);
    }

    public AvatarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AvatarView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_avatar, this, true);
        tvInitial = findViewById(R.id.tvAvatarInitial);
        ivPhoto = findViewById(R.id.ivAvatarPhoto);
    }

    //Gán tên và ảnh. Gọi lại nhiều lần an toàn (dòng RecyclerView được tái sử dụng): đổi người thì request tải ảnh cũ bị hủy
    public void bind(@Nullable String displayName, @Nullable String avatarUrl) {
        if (bound && Objects.equals(displayName, boundName) && Objects.equals(avatarUrl, boundUrl)) {
            return;
        }
        bound = true;
        boundName = displayName;
        boundUrl = avatarUrl;

        Glide.with(getContext()).clear(ivPhoto);
        ivPhoto.setImageDrawable(null);
        ivPhoto.setVisibility(INVISIBLE);
        tvInitial.setText(initialOf(displayName));
        tvInitial.setVisibility(VISIBLE);

        if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
            return;
        }
        Glide.with(getContext())
                .load(avatarUrl)
                .circleCrop()
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, @Nullable Object model,
                                                @NonNull Target<Drawable> target, boolean isFirstResource) {
                        //Lỗi thì giữ nguyên chữ cái đầu
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model,
                                                   Target<Drawable> target, @NonNull DataSource dataSource,
                                                   boolean isFirstResource) {
                        tvInitial.setVisibility(INVISIBLE);
                        ivPhoto.setVisibility(VISIBLE);
                        return false;
                    }
                })
                .into(ivPhoto);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        tvInitial.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(w, h) * INITIAL_TEXT_RATIO);
    }

    //Chữ cái đầu viết hoa của tên; tên trống thì hiện "?"
    private static String initialOf(@Nullable String displayName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            return "?";
        }
        int firstCodePoint = Character.toUpperCase(name.codePointAt(0));
        return new String(Character.toChars(firstCodePoint));
    }
}