package com.example.lori.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;

import java.io.ByteArrayOutputStream;

//Chuẩn bị ảnh để gửi nhận diện: đọc từ Uri, xoay thẳng theo EXIF, thu nhỏ, nén JPEG. Gọi từ luồng nền
public final class ImagePreparer {

    //Cạnh dài tối đa. Ở cỡ này ảnh JPEG chỉ vài trăm KB, xa mức 3 MB mà Backend cho phép
    private static final int MAX_EDGE = 1280;
    private static final int JPEG_QUALITY = 85;

    private ImagePreparer() {
    }

    //bitmap: ảnh để hiển thị; jpeg: đúng ảnh đó đã nén để gửi. Backend trả box theo ảnh này nên hai thứ phải khớp nhau
    public static final class PreparedImage {
        public final Bitmap bitmap;
        public final byte[] jpeg;

        PreparedImage(Bitmap bitmap, byte[] jpeg) {
            this.bitmap = bitmap;
            this.jpeg = jpeg;
        }
    }

    public static PreparedImage prepare(Context context, Uri uri) throws Exception {
        //Glide tự xoay ảnh thẳng theo EXIF; AT_MOST chỉ thu nhỏ, không phóng to ảnh nhỏ.
        //Bitmap phần cứng không nén được JPEG nên phải tắt
        Bitmap bitmap = Glide.with(context.getApplicationContext())
                .asBitmap()
                .load(uri)
                .downsample(DownsampleStrategy.AT_MOST)
                .disallowHardwareConfig()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .submit(MAX_EDGE, MAX_EDGE)
                .get();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        //shortcut: ảnh PNG có nền trong suốt sẽ thành nền đen khi nén JPEG, nâng cấp khi thấy ảnh thật bị vậy
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        return new PreparedImage(bitmap, out.toByteArray());
    }
}