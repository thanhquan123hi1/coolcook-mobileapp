
import os

content = """<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/friendInviteRoot"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#FF000000">

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true">

        <androidx.constraintlayout.widget.ConstraintLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:paddingTop="32dp"
            android:paddingBottom="24dp">

            <com.google.android.material.card.MaterialCardView
                android:id="@+id/inviteCard"
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="24dp"
                android:layout_marginTop="60dp"
                android:layout_marginEnd="24dp"
                app:cardBackgroundColor="@android:color/transparent"
                app:cardCornerRadius="44dp"
                app:cardElevation="0dp"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toTopOf="parent">

                <androidx.constraintlayout.widget.ConstraintLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_invite_screen_card"
                    android:paddingTop="80dp"
                    android:paddingBottom="24dp"
                    android:paddingStart="24dp"
                    android:paddingEnd="24dp">

                    <ImageView
                        android:layout_width="20dp"
                        android:layout_height="20dp"
                        android:layout_marginStart="8dp"
                        android:layout_marginTop="20dp"
                        android:src="@drawable/ic_invite_heart_outline"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <ImageView
                        android:layout_width="24dp"
                        android:layout_height="24dp"
                        android:layout_marginEnd="16dp"
                        android:layout_marginTop="10dp"
                        android:src="@drawable/ic_invite_sparkle"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <ImageView
                        android:layout_width="16dp"
                        android:layout_height="16dp"
                        android:layout_marginStart="0dp"
                        android:layout_marginTop="60dp"
                        android:src="@drawable/ic_invite_star"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <TextView
                        android:id="@+id/txtInviteTitle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="20dp"
                        android:fontFamily="@font/be_vietnam_pro_bold"
                        android:gravity="center"
                        android:text="K?t b?n trên CoolCook"
                        android:textColor="#FF111111"
                        android:textSize="26sp"
                        android:textStyle="bold"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toTopOf="parent" />

                    <TextView
                        android:id="@+id/txtInviteSubtitle"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="12dp"
                        android:fontFamily="@font/be_vietnam_pro_medium"
                        android:gravity="center"
                        android:lineSpacingExtra="4dp"
                        android:text="M? này là nh?n di?n c? ð?nh\nc?a b?n trên CoolCook."
                        android:textColor="#FF4A4036"
                        android:textSize="15sp"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/txtInviteTitle" />

                    <ProgressBar
                        android:id="@+id/inviteLoading"
                        android:layout_width="34dp"
                        android:layout_height="34dp"
                        android:layout_marginTop="12dp"
                        android:visibility="gone"
                        android:indeterminateTint="#F6AF52"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/txtInviteSubtitle" />

                    <LinearLayout
                        android:id="@+id/llStatusPill"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="16dp"
                        android:background="@drawable/bg_invite_status_pill"
                        android:gravity="center"
                        android:orientation="horizontal"
                        android:paddingHorizontal="16dp"
                        android:paddingVertical="8dp"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/inviteLoading">

                        <TextView
                            android:id="@+id/txtInviteStatus"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:fontFamily="@font/be_vietnam_pro_bold"
                            android:text="? M? k?t b?n c?a b?n ð? s?n sàng!"
                            android:textColor="#E89A0E"
                            android:textSize="13sp" />
                    </LinearLayout>

                    <androidx.constraintlayout.widget.ConstraintLayout
                        android:id="@+id/boxFriendCode"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="20dp"
                        android:background="@drawable/bg_invite_code_box"
                        android:minHeight="72dp"
                        android:padding="16dp"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/llStatusPill">

                        <TextView
                            android:id="@+id/txtFriendInviteCode"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:fontFamily="@font/be_vietnam_pro_bold"
                            android:letterSpacing="0.05"
                            android:text="--------"
                            android:textColor="#FF111111"
                            android:textSize="28sp"
                            android:textStyle="bold"
                            app:layout_constraintBottom_toBottomOf="parent"
                            app:layout_constraintEnd_toStartOf="@+id/icCopyCode"
                            app:layout_constraintHorizontal_chainStyle="packed"
                            app:layout_constraintStart_toStartOf="parent"
                            app:layout_constraintTop_toTopOf="parent" />

                        <ImageView
                            android:id="@+id/icCopyCode"
                            android:layout_width="26dp"
                            android:layout_height="26dp"
                            android:layout_marginStart="16dp"
                            android:src="@drawable/ic_invite_copy"
                            app:layout_constraintBottom_toBottomOf="parent"
                            app:layout_constraintEnd_toEndOf="parent"
                            app:layout_constraintStart_toEndOf="@+id/txtFriendInviteCode"
                            app:layout_constraintTop_toTopOf="parent" />
                    </androidx.constraintlayout.widget.ConstraintLayout>

                    <LinearLayout
                        android:id="@+id/btnShareInviteCode"
                        android:layout_width="0dp"
                        android:layout_height="64dp"
                        android:layout_marginTop="16dp"
                        android:background="@drawable/bg_invite_action_row"
                        android:gravity="center_vertical"
                        android:orientation="horizontal"
                        android:paddingHorizontal="20dp"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/boxFriendCode">

                        <ImageView
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@drawable/ic_invite_send" />

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="16dp"
                            android:layout_weight="1"
                            android:fontFamily="@font/be_vietnam_pro_bold"
                            android:text="G?i l?i m?i k?t b?n"
                            android:textColor="#FF111111"
                            android:textSize="16sp" />

                        <ImageView
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@drawable/ic_invite_chevron_right" />
                    </LinearLayout>

                    <LinearLayout
                        android:id="@+id/rowEnterFriendCode"
                        android:layout_width="0dp"
                        android:layout_height="64dp"
                        android:layout_marginTop="12dp"
                        android:background="@drawable/bg_invite_action_row"
                        android:gravity="center_vertical"
                        android:orientation="horizontal"
                        android:paddingHorizontal="20dp"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/btnShareInviteCode">

                        <ImageView
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@drawable/ic_invite_person_add" />

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="16dp"
                            android:layout_weight="1"
                            android:fontFamily="@font/be_vietnam_pro_bold"
                            android:text="Nh?p m? b?n bè"
                            android:textColor="#FF111111"
                            android:textSize="16sp" />

                        <ImageView
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@drawable/ic_invite_chevron_right" />
                    </LinearLayout>

                    <EditText
                        android:id="@+id/edtFriendInviteCode"
                        android:layout_width="0dp"
                        android:layout_height="56dp"
                        android:layout_marginTop="8dp"
                        android:background="@drawable/bg_invite_code_box"
                        android:fontFamily="@font/be_vietnam_pro_medium"
                        android:hint="Ví d?: OL869001"
                        android:paddingHorizontal="16dp"
                        android:textColor="#FF111111"
                        android:textSize="16sp"
                        android:visibility="gone"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/rowEnterFriendCode" />

                    <LinearLayout
                        android:id="@+id/btnAcceptInvite"
                        android:layout_width="0dp"
                        android:layout_height="64dp"
                        android:layout_marginTop="12dp"
                        android:background="@drawable/bg_invite_action_row"
                        android:gravity="center_vertical"
                        android:orientation="horizontal"
                        android:paddingHorizontal="20dp"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/edtFriendInviteCode">

                        <ImageView
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@drawable/ic_invite_group" />

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="16dp"
                            android:layout_weight="1"
                            android:fontFamily="@font/be_vietnam_pro_bold"
                            android:text="K?t b?n"
                            android:textColor="#FF111111"
                            android:textSize="16sp" />

                        <ImageView
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@drawable/ic_invite_chevron_right" />
                    </LinearLayout>

                    <LinearLayout
                        android:id="@+id/btnInviteReject"
                        android:layout_width="0dp"
                        android:layout_height="64dp"
                        android:layout_marginTop="12dp"
                        android:background="@drawable/bg_invite_reject_row"
                        android:gravity="center_vertical"
                        android:orientation="horizontal"
                        android:paddingHorizontal="20dp"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/btnAcceptInvite">

                        <ImageView
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@drawable/ic_invite_block" />

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_marginStart="16dp"
                            android:layout_weight="1"
                            android:fontFamily="@font/be_vietnam_pro_bold"
                            android:text="T? ch?i"
                            android:textColor="#E53935"
                            android:textSize="16sp" />

                        <ImageView
                            android:layout_width="24dp"
                            android:layout_height="24dp"
                            android:src="@drawable/ic_invite_chevron_right"
                            app:tint="#E53935" />
                    </LinearLayout>

                    <TextView
                        android:id="@+id/btnInviteClose"
                        android:layout_width="wrap_content"
                        android:layout_height="48dp"
                        android:layout_marginTop="24dp"
                        android:fontFamily="@font/be_vietnam_pro_bold"
                        android:gravity="center"
                        android:paddingHorizontal="24dp"
                        android:text="Ðóng"
                        android:textColor="#FF8D7A65"
                        android:textSize="16sp"
                        app:layout_constraintEnd_toEndOf="parent"
                        app:layout_constraintStart_toStartOf="parent"
                        app:layout_constraintTop_toBottomOf="@+id/btnInviteReject" />

                </androidx.constraintlayout.widget.ConstraintLayout>
            </com.google.android.material.card.MaterialCardView>

            <FrameLayout
                android:layout_width="128dp"
                android:layout_height="128dp"
                android:background="@drawable/bg_invite_avatar_ring"
                android:elevation="4dp"
                app:layout_constraintBottom_toTopOf="@+id/inviteCard"
                app:layout_constraintEnd_toEndOf="parent"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toTopOf="@+id/inviteCard">

                <com.google.android.material.card.MaterialCardView
                    android:layout_width="120dp"
                    android:layout_height="120dp"
                    android:layout_gravity="center"
                    app:cardCornerRadius="60dp"
                    app:cardElevation="0dp"
                    app:strokeWidth="0dp">

                    <ImageView
                        android:id="@+id/imgInviteAvatar"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:scaleType="centerCrop"
                        android:src="@drawable/img_home_profile" />
                </com.google.android.material.card.MaterialCardView>
            </FrameLayout>

        </androidx.constraintlayout.widget.ConstraintLayout>
    </androidx.core.widget.NestedScrollView>
</androidx.constraintlayout.widget.ConstraintLayout>
"""

with open("app/src/main/res/layout/activity_friend_invite.xml", "wb") as f:
    f.write(content.encode("utf-8"))

