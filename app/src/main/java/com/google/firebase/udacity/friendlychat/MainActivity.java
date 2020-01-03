
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

	public static final String ANONYMOUS = "anonymous";
	public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
	private static final String TAG = "MainActivity";
	private static final int RC_PHOTO_PICKER = 2;
	private FirebaseDatabase mFirebaseDatabase;
	private DatabaseReference mDatabaseMessageReference;
	private ChildEventListener mChildEventListener;
	private FirebaseAuth mfirebaseAuth;
	private FirebaseAuth.AuthStateListener mAuthstateListener;


	private ListView mMessageListView;
	private MessageAdapter mMessageAdapter;
	private ProgressBar mProgressBar;
	private ImageButton mPhotoPickerButton;
	private EditText mMessageEditText;
	private Button mSendButton;
	private FirebaseStorage mFirebaseStorage;
	private StorageReference mChatPhotosReference;

	private String mUsername;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);


		mUsername = ANONYMOUS;

		// Initialize references to views
		mFirebaseDatabase = FirebaseDatabase.getInstance();git

		mfirebaseAuth = FirebaseAuth.getInstance();

		mFirebaseStorage = FirebaseStorage.getInstance();
		mChatPhotosReference = mFirebaseStorage.getReference().child("chat_photos");

		mDatabaseMessageReference = mFirebaseDatabase.getReference().child("messages");


		mProgressBar = findViewById(R.id.progressBar);
		mMessageListView = findViewById(R.id.messageListView);
		mPhotoPickerButton = findViewById(R.id.photoPickerButton);
		mMessageEditText = findViewById(R.id.messageEditText);
		mSendButton = findViewById(R.id.sendButton);

		// Initialize message ListView and its adapter
		List<FriendlyMessage> friendlyMessages = new ArrayList<>();
		mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
		mMessageListView.setAdapter(mMessageAdapter);

		// Initialize progress bar
		mProgressBar.setVisibility(ProgressBar.INVISIBLE);

		// ImagePickerButton shows an image picker to upload a image for a message
		mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
				intent.setType("image/jpeg");
				intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
				startActivityForResult(Intent.createChooser(intent, "complete action using"), RC_PHOTO_PICKER);


			}
		});

		// Enable Send button when there's text to send
		mMessageEditText.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
			}

			@Override
			public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				if (charSequence.toString().trim().length() > 0) {
					mSendButton.setEnabled(true);
				} else {
					mSendButton.setEnabled(false);
				}
			}

			@Override
			public void afterTextChanged(Editable editable) {
			}
		});
		mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});


		mSendButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {

				FriendlyMessage mFriendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
				mDatabaseMessageReference.push().setValue(mFriendlyMessage);

				mMessageEditText.setText("");
				mDatabaseMessageReference.addChildEventListener(mChildEventListener);

			}
		});
		mAuthstateListener = new FirebaseAuth.AuthStateListener() {
			@Override
			public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {


				FirebaseUser user = firebaseAuth.getCurrentUser();


				if (user != null) {
					//user is signedin

					onSignedInInitializer(user.getDisplayName());
				} else {
					//user is signed out
					// Choose authentication providers
					onSignedOutCleanUp();
					List<AuthUI.IdpConfig> providers = Arrays.asList(
							new AuthUI.IdpConfig.EmailBuilder().build(),
							new AuthUI.IdpConfig.GoogleBuilder().build());

					// Create and launch sign-in intent
					startActivityForResult(
							AuthUI.getInstance()
									.createSignInIntentBuilder()
									.setAvailableProviders(providers)
									.setIsSmartLockEnabled(false)
									.build(),
							1);
				}


			}


		};


	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == 1) {
			if (resultCode == RESULT_OK) {

				Toast.makeText(this, "Signed in", Toast.LENGTH_LONG).show();
			} else if (requestCode == RESULT_CANCELED) {
				Toast.makeText(this, "Not signed in", Toast.LENGTH_LONG).show();
				finish();

			} else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {

				final Uri selectedImagaeUri = data.getData();
				final StorageReference storageReference = mChatPhotosReference.child(selectedImagaeUri.getLastPathSegment());
//				storageReference.putFile(selectedImagaeUri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
//					@Override
//					public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
//
//						FriendlyMessage friendlyMessage = new FriendlyMessage(null,mUsername,selectedImagaeUri.toString());
//
//						mDatabaseMessageReference.push().setValue(friendlyMessage);
//
//					}
//				});

				storageReference.putFile(selectedImagaeUri).continueWithTask(new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
					@Override
					public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task) {
						if (!task.isSuccessful()) {

						}
						return storageReference.getDownloadUrl();

					}


				}).addOnCompleteListener(new OnCompleteListener<Uri>() {
					@Override
					public void onComplete(@NonNull Task<Uri> task) {

						if (task.isSuccessful()) {
							Uri downloadUri = task.getResult();

							FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, downloadUri.toString());
							mDatabaseMessageReference.push().setValue(friendlyMessage);

						} else {
							Toast.makeText(MainActivity.this, "upload failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
						}

					}
				});


			}

		}

	}

	@Override
	protected void onResume() {
		super.onResume();
		mfirebaseAuth.addAuthStateListener(mAuthstateListener);
	}

	@Override
	protected void onPause() {
		super.onPause();
		mfirebaseAuth.removeAuthStateListener(mAuthstateListener);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {


		switch (item.getItemId()) {

			case R.id.sign_out_menu:
				AuthUI.getInstance().signOut(this);
				return true;


		}


		return super.onOptionsItemSelected(item);
	}


	private void onSignedInInitializer(String displayName) {
		mUsername = displayName;
		if (mChildEventListener == null)
			addChildEventListener();
		mDatabaseMessageReference.addChildEventListener(mChildEventListener);

	}



	public void onSignedOutCleanUp() {
		mUsername = ANONYMOUS;
		mMessageAdapter.clear();

		if (mChildEventListener != null)
			mDatabaseMessageReference.removeEventListener(mChildEventListener);

	}







	public void addChildEventListener() {


		mChildEventListener = new ChildEventListener() {
			@Override
			public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

				FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
				mMessageAdapter.add(friendlyMessage);
			}

			@Override
			public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

			}

			@Override
			public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

			}

			@Override
			public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

			}

			@Override
			public void onCancelled(@NonNull DatabaseError databaseError) {

			}

		};

		mDatabaseMessageReference.addChildEventListener(mChildEventListener);
	}



}
