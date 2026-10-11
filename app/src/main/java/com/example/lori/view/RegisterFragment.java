package com.example.lori.view;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.example.lori.AuthActivity;
import com.example.lori.R;
import com.example.lori.viewmodel.AuthViewModel;
import com.example.lori.utils.GoogleSignInHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

//Fragment màn Đăng ký tài khoản bằng email: chỉ hiển thị và bắt sự kiện, nghiệp vụ nằm ở AuthViewModel
public class RegisterFragment extends Fragment {

    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_register, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        TextView tabLogin = view.findViewById(R.id.tabLogin);
        TextInputLayout layoutName = view.findViewById(R.id.layoutName);
        TextInputEditText etName = view.findViewById(R.id.etName);
        TextInputLayout layoutEmail = view.findViewById(R.id.layoutEmail);
        TextInputEditText etEmail = view.findViewById(R.id.etEmail);
        TextInputLayout layoutPassword = view.findViewById(R.id.layoutPassword);
        TextInputEditText etPassword = view.findViewById(R.id.etPassword);
        TextInputLayout layoutConfirmPassword = view.findViewById(R.id.layoutConfirmPassword);
        TextInputEditText etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        TextView tvFormError = view.findViewById(R.id.tvFormError);
        LinearProgressIndicator progress = view.findViewById(R.id.progressAuth);
        MaterialButton btnRegister = view.findViewById(R.id.btnRegister);
        MaterialButton btnGoogle = view.findViewById(R.id.btnGoogle);
        btnGoogle.setOnClickListener(v -> startGoogleSignIn());

        Runnable submit = () -> {
            hideKeyboard(view);
            viewModel.register(textOf(etName), textOf(etEmail),
                    textOf(etPassword), textOf(etConfirmPassword));
        };
        btnRegister.setOnClickListener(v -> submit.run());
        //Bấm nút "Xong" trên bàn phím ở ô xác nhận mật khẩu cũng gửi form
        etConfirmPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });

        //Chuyển sang tab Đăng nhập
        tabLogin.setOnClickListener(v -> Navigation.findNavController(v)
                .navigate(R.id.action_registerFragment_to_loginFragment));

        viewModel.getNameError().observe(getViewLifecycleOwner(),
                res -> layoutName.setError(res == null ? null : getString(res)));
        viewModel.getEmailError().observe(getViewLifecycleOwner(),
                res -> layoutEmail.setError(res == null ? null : getString(res)));
        viewModel.getPasswordError().observe(getViewLifecycleOwner(),
                res -> layoutPassword.setError(res == null ? null : getString(res)));
        viewModel.getConfirmPasswordError().observe(getViewLifecycleOwner(),
                res -> layoutConfirmPassword.setError(res == null ? null : getString(res)));
        viewModel.getFormError().observe(getViewLifecycleOwner(), res -> {
            tvFormError.setText(res == null ? null : getString(res));
            tvFormError.setVisibility(res == null ? View.GONE : View.VISIBLE);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            boolean busy = Boolean.TRUE.equals(isLoading);
            progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
            btnRegister.setEnabled(!busy);
            btnGoogle.setEnabled(!busy);
            tabLogin.setEnabled(!busy);
        });
        viewModel.getAuthenticated().observe(getViewLifecycleOwner(), authenticated -> {
            if (Boolean.TRUE.equals(authenticated)) {
                ((AuthActivity) requireActivity()).openMain();
            }
        });
    }

    //Mở bảng chọn tài khoản Google, lấy ID Token rồi nhờ ViewModel gửi lên Backend
    private void startGoogleSignIn() {
        if (!viewModel.startGoogleSignIn()) {
            return;
        }
        //Callback chỉ gọi ViewModel (sống qua xoay màn hình), không đụng tới View nên an toàn
        GoogleSignInHelper.signIn(requireActivity(), new GoogleSignInHelper.Callback() {
            @Override
            public void onIdToken(String idToken) {
                viewModel.loginWithGoogle(idToken);
            }

            @Override
            public void onCancelled() {
                viewModel.cancelGoogleSignIn();
            }

            @Override
            public void onError(int messageRes) {
                viewModel.failGoogleSignIn(messageRes);
            }
        });
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}