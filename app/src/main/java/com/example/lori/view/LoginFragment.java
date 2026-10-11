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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

//Fragment màn Đăng nhập bằng email + mật khẩu: chỉ hiển thị và bắt sự kiện, nghiệp vụ nằm ở AuthViewModel
public class LoginFragment extends Fragment {

    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        TextView tabRegister = view.findViewById(R.id.tabRegister);
        TextInputLayout layoutEmail = view.findViewById(R.id.layoutEmail);
        TextInputEditText etEmail = view.findViewById(R.id.etEmail);
        TextInputLayout layoutPassword = view.findViewById(R.id.layoutPassword);
        TextInputEditText etPassword = view.findViewById(R.id.etPassword);
        TextView tvFormError = view.findViewById(R.id.tvFormError);
        LinearProgressIndicator progress = view.findViewById(R.id.progressAuth);
        MaterialButton btnLogin = view.findViewById(R.id.btnLogin);

        Runnable submit = () -> {
            hideKeyboard(view);
            viewModel.login(textOf(etEmail), textOf(etPassword));
        };
        btnLogin.setOnClickListener(v -> submit.run());
        //Bấm nút "Xong" trên bàn phím ở ô mật khẩu cũng gửi form
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit.run();
                return true;
            }
            return false;
        });

        //Chuyển sang tab Đăng ký
        tabRegister.setOnClickListener(v -> Navigation.findNavController(v)
                .navigate(R.id.action_loginFragment_to_registerFragment));

        viewModel.getEmailError().observe(getViewLifecycleOwner(),
                res -> layoutEmail.setError(res == null ? null : getString(res)));
        viewModel.getPasswordError().observe(getViewLifecycleOwner(),
                res -> layoutPassword.setError(res == null ? null : getString(res)));
        viewModel.getFormError().observe(getViewLifecycleOwner(), res -> {
            tvFormError.setText(res == null ? null : getString(res));
            tvFormError.setVisibility(res == null ? View.GONE : View.VISIBLE);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), isLoading -> {
            boolean busy = Boolean.TRUE.equals(isLoading);
            progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
            btnLogin.setEnabled(!busy);
            tabRegister.setEnabled(!busy);
        });
        viewModel.getAuthenticated().observe(getViewLifecycleOwner(), authenticated -> {
            if (Boolean.TRUE.equals(authenticated)) {
                ((AuthActivity) requireActivity()).openMain();
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