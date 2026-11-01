package com.example.lori.view;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.lori.R;
import com.example.lori.data.remote.dto.AiImageResponse;
import com.example.lori.utils.SpeechHelper;
import com.example.lori.viewmodel.ImageLearningViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Collections;
import java.util.List;

//Màn Nhận diện ảnh (Premium): chụp bằng CameraX hoặc chọn từ thư viện → AI nhận diện vật → hiện khung + từ vựng + ví dụ → lưu vào Từ vựng của tôi
public class ImageLearningFragment extends Fragment {

    private ImageLearningViewModel viewModel;
    private SpeechHelper speechHelper;
    private ImageWordAdapter adapter;

    private PreviewView previewView;
    private TextView tvCameraHint;
    private FloatingActionButton btnShutter;
    private DetectionImageView imageDetection;
    private TextView tvResultTitle;
    private TextView tvEmpty;
    private MaterialCardView cardExample;
    private TextView tvExampleTitle;
    private TextView tvExample;
    private TextView tvExampleVi;
    private MaterialButton btnSaveAll;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    //Chỉ hỏi quyền camera một lần mỗi lần mở màn; các lần quay lại khu chụp sau đó chỉ hiện dòng gợi ý
    private boolean cameraPermissionAsked;

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    showCameraHint(R.string.image_camera_permission_needed);
                }
            });

    //Photo Picker: không cần xin quyền đọc bộ nhớ
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    viewModel.identify(uri);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_image_learning, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ImageLearningViewModel.class);
        speechHelper = SpeechHelper.getInstance(requireContext());

        View layoutCapture = view.findViewById(R.id.layoutImageCapture);
        View layoutLoading = view.findViewById(R.id.layoutImageLoading);
        View layoutResult = view.findViewById(R.id.layoutImageResult);
        previewView = view.findViewById(R.id.previewImage);
        tvCameraHint = view.findViewById(R.id.tvCameraHint);
        TextView tvOffline = view.findViewById(R.id.tvImageOffline);
        TextView tvError = view.findViewById(R.id.tvImageError);
        MaterialButton btnGallery = view.findViewById(R.id.btnImageGallery);
        btnShutter = view.findViewById(R.id.btnImageShutter);
        imageDetection = view.findViewById(R.id.imageDetection);
        tvResultTitle = view.findViewById(R.id.tvImageResultTitle);
        tvEmpty = view.findViewById(R.id.tvImageEmpty);
        RecyclerView rvWords = view.findViewById(R.id.rvImageWords);
        cardExample = view.findViewById(R.id.cardImageExample);
        tvExampleTitle = view.findViewById(R.id.tvImageExampleTitle);
        tvExample = view.findViewById(R.id.tvImageExample);
        tvExampleVi = view.findViewById(R.id.tvImageExampleVi);
        btnSaveAll = view.findViewById(R.id.btnImageSaveAll);
        MaterialButton btnRetake = view.findViewById(R.id.btnImageRetake);

        view.findViewById(R.id.btnBackImageLearning)
                .setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        adapter = new ImageWordAdapter(new ImageWordAdapter.Listener() {
            @Override
            public void onSelect(int position) {
                viewModel.select(position);
            }

            @Override
            public void onSpeak(String word) {
                speak(word);
            }
        });
        rvWords.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvWords.setAdapter(adapter);

        btnShutter.setOnClickListener(v -> takePhoto());
        btnGallery.setOnClickListener(v -> pickImage());
        btnSaveAll.setOnClickListener(v -> viewModel.saveAll());
        btnRetake.setOnClickListener(v -> viewModel.retake());

        //Chỉ hiện một khu theo trạng thái; camera chỉ chạy ở khu chụp ảnh
        viewModel.getState().observe(getViewLifecycleOwner(), state -> {
            layoutCapture.setVisibility(state == ImageLearningViewModel.STATE_CAPTURE ? View.VISIBLE : View.GONE);
            layoutLoading.setVisibility(state == ImageLearningViewModel.STATE_LOADING ? View.VISIBLE : View.GONE);
            layoutResult.setVisibility(state == ImageLearningViewModel.STATE_RESULT ? View.VISIBLE : View.GONE);
            if (state == ImageLearningViewModel.STATE_CAPTURE) {
                startCameraIfPermitted();
            } else {
                stopCamera();
            }
        });

        //Chụp/chọn ảnh cần Internet: offline (hoặc đang nhận diện) thì khóa nút; offline thì hiện thêm dòng giải thích
        viewModel.getCaptureEnabled().observe(getViewLifecycleOwner(), enabled -> {
            btnShutter.setEnabled(Boolean.TRUE.equals(enabled));
            btnGallery.setEnabled(Boolean.TRUE.equals(enabled));
        });
        viewModel.getOnline().observe(getViewLifecycleOwner(), online ->
                tvOffline.setVisibility(Boolean.TRUE.equals(online) ? View.GONE : View.VISIBLE));

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), messageRes -> {
            if (messageRes == null) {
                tvError.setVisibility(View.GONE);
            } else {
                tvError.setText(messageRes);
                tvError.setVisibility(View.VISIBLE);
            }
        });

        viewModel.getPhoto().observe(getViewLifecycleOwner(), bitmap -> imageDetection.setImageBitmap(bitmap));
        viewModel.getObjects().observe(getViewLifecycleOwner(), this::renderObjects);
        viewModel.getSelectedIndex().observe(getViewLifecycleOwner(), index -> renderSelection());
        viewModel.getSaved().observe(getViewLifecycleOwner(), saved -> renderSaveButton());

        viewModel.getSavedCount().observe(getViewLifecycleOwner(), count -> {
            if (count == null) {
                return;
            }
            Toast.makeText(requireContext(),
                    count > 0 ? getString(R.string.image_saved_format, count) : getString(R.string.image_saved_already),
                    Toast.LENGTH_SHORT).show();
            viewModel.consumeSavedCount();
        });

        //Premium hết hạn giữa chừng: Backend trả 403 PREMIUM_REQUIRED nên mở dialog nâng cấp
        viewModel.getPremiumRequired().observe(getViewLifecycleOwner(), required -> {
            if (Boolean.TRUE.equals(required)) {
                PremiumPromptDialog.newInstance(getString(R.string.image_feature_name))
                        .show(getChildFragmentManager(), "premium_prompt");
                viewModel.consumePremiumRequired();
            }
        });
    }

    //Danh sách vật mới: cập nhật tiêu đề, danh sách, nút lưu và phần chọn
    private void renderObjects(@Nullable List<AiImageResponse.ImageObject> list) {
        List<AiImageResponse.ImageObject> items = list == null ? Collections.emptyList() : list;
        adapter.submitList(items);
        tvResultTitle.setText(getString(R.string.image_result_title_format, items.size()));
        tvResultTitle.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        btnSaveAll.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        renderSaveButton();
        renderSelection();
    }

    //Vật đang chọn: tô khung trên ảnh, tô dòng trong danh sách, hiện câu ví dụ của vật đó
    private void renderSelection() {
        List<AiImageResponse.ImageObject> items = viewModel.getObjects().getValue();
        Integer index = viewModel.getSelectedIndex().getValue();
        if (items == null || items.isEmpty() || index == null || index >= items.size()) {
            imageDetection.setObjects(Collections.emptyList(), 0);
            cardExample.setVisibility(View.GONE);
            return;
        }
        imageDetection.setObjects(items, index);
        adapter.setSelectedIndex(index);

        AiImageResponse.ImageObject selected = items.get(index);
        boolean hasExample = selected.example != null && !selected.example.trim().isEmpty();
        cardExample.setVisibility(hasExample ? View.VISIBLE : View.GONE);
        if (hasExample) {
            tvExampleTitle.setText(getString(R.string.image_example_title_format, selected.word));
            tvExample.setText(getString(R.string.image_example_text_format, selected.example));
            boolean hasVi = selected.exampleVi != null && !selected.exampleVi.trim().isEmpty();
            tvExampleVi.setVisibility(hasVi ? View.VISIBLE : View.GONE);
            tvExampleVi.setText(selected.exampleVi);
        }
    }

    //Nút lưu: hiện số từ; sau khi lưu thì khóa và đổi chữ thành "Đã lưu"
    private void renderSaveButton() {
        List<AiImageResponse.ImageObject> items = viewModel.getObjects().getValue();
        boolean saved = Boolean.TRUE.equals(viewModel.getSaved().getValue());
        btnSaveAll.setEnabled(!saved);
        btnSaveAll.setText(saved
                ? getString(R.string.image_save_done)
                : getString(R.string.image_save_all_format, items == null ? 0 : items.size()));
    }

    private void speak(String word) {
        if (!speechHelper.speak(word, "image_word_utt")) {
            Toast.makeText(requireContext(), R.string.tts_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void pickImage() {
        try {
            pickImageLauncher.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        } catch (ActivityNotFoundException e) {
            //Máy không có ứng dụng nào để chọn ảnh (có thể gặp trên một số máy ảo)
            viewModel.showError(R.string.image_error_gallery_unavailable);
        }
    }

    //Bật camera nếu đã có quyền; chưa có thì hỏi (một lần) hoặc hiện gợi ý
    private void startCameraIfPermitted() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (!cameraPermissionAsked) {
            cameraPermissionAsked = true;
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        } else {
            showCameraHint(R.string.image_camera_permission_needed);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(requireContext());
        future.addListener(() -> {
            //Màn hình có thể đã bị đóng trong lúc chờ camera sẵn sàng
            if (getView() == null) {
                return;
            }
            try {
                cameraProvider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(getViewLifecycleOwner(), CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture);
                tvCameraHint.setVisibility(View.GONE);
            } catch (Exception e) {
                //Máy không có camera sau (vd máy ảo) hoặc camera đang bị app khác dùng
                imageCapture = null;
                showCameraHint(R.string.image_camera_unavailable);
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void stopCamera() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        imageCapture = null;
    }

    private void showCameraHint(@StringRes int messageRes) {
        tvCameraHint.setText(messageRes);
        tvCameraHint.setVisibility(View.VISIBLE);
    }

    //Chụp vào file tạm trong cacheDir rồi đưa Uri cho ViewModel (ViewModel xóa file sau khi đọc)
    private void takePhoto() {
        if (imageCapture == null) {
            return;
        }
        //Khóa nút tới khi ViewModel chuyển sang "đang nhận diện" (hoặc chụp lỗi), tránh chụp đúp
        btnShutter.setEnabled(false);
        File file = new File(requireContext().getCacheDir(), "identify_" + System.currentTimeMillis() + ".jpg");
        imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        viewModel.identify(Uri.fromFile(file));
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        btnShutter.setEnabled(true);
                        viewModel.showError(R.string.image_error_capture);
                    }
                });
    }

    @Override
    public void onDestroyView() {
        cameraProvider = null;
        imageCapture = null;
        super.onDestroyView();
    }
}