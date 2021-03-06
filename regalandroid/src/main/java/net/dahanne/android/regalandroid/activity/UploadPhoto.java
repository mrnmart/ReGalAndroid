/**
 *  ReGalAndroid, a gallery client for Android, supporting G2, G3, etc...
 *  URLs: https://github.com/anthonydahanne/ReGalAndroid , http://blog.dahanne.net
 *  Copyright (c) 2010 Anthony Dahanne
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package net.dahanne.android.regalandroid.activity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import net.dahanne.android.regalandroid.R;
import net.dahanne.android.regalandroid.RegalAndroidApplication;
import net.dahanne.android.regalandroid.tasks.AddPhotoTask;
import net.dahanne.android.regalandroid.tasks.AddPhotosTask;
import net.dahanne.android.regalandroid.tasks.FetchAlbumForUploadTask;
import net.dahanne.android.regalandroid.tasks.LoginTask;
import net.dahanne.android.regalandroid.utils.AndroidUriUtils;
import net.dahanne.android.regalandroid.utils.DBUtils;
import net.dahanne.android.regalandroid.utils.ShowUtils;
import net.dahanne.gallery.commons.model.Album;
import net.dahanne.gallery.commons.utils.AlbumUtils;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class UploadPhoto extends Activity implements OnClickListener {

	private static final String SENT_WITH_REGALANDROID = "SentWithRegalAndroid";
	private ProgressDialog progressDialog;
	private Button sendButton;
	private Button cancelButton;
	private Button goToReGalAndroidButton;
	private TextView galleryUrlText;
	private TextView connectedAsUserText;
	private Uri mImageUri;
	private Spinner spinner;
	private ArrayAdapter<Album> albumAdapter;
	private EditText filenameEditText;
	private File imageFromCamera;
	private ArrayList<Uri> mImageUris;
	private RegalAndroidApplication 		application;

	public UploadPhoto() {
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		albumAdapter = new ArrayAdapter<Album>(this, 0);
		// albumAdapter = new AlbumAdapterForUpload(this,
		// R.layout.show_albums_for_upload_row, items);
		// this will enable the progress bar
		requestWindowFeature(Window.FEATURE_PROGRESS);
		setContentView(R.layout.upload_photo);
		setTitle(R.string.upload_photo_title);
		filenameEditText = (EditText) findViewById(R.id.filename);

		Intent intent = getIntent();
		Bundle extras = intent.getExtras();

		String fileName = SENT_WITH_REGALANDROID;

		if (Intent.ACTION_SEND.equals(intent.getAction())) {
			if ((extras != null) || (intent.getData() != null)) {
				// depending on the source of the intent, the uri can be in
				// extra or data, or can also be a bmp if coming from the camera
				if (extras != null) {
					mImageUri = (Uri) extras.getParcelable(Intent.EXTRA_STREAM);
					if (mImageUri == null) {
						Bitmap bm = null;
						Object o = extras.get("data");
						if ((o != null) && (o instanceof Bitmap)) {
							bm = (Bitmap) o;
							try {
								StringBuilder stringBuilder = new StringBuilder();
								stringBuilder.append(Settings
										.getReGalAndroidPath(this));
								stringBuilder.append("/");
								StringBuilder stringBuilderFileName = new StringBuilder();
								stringBuilderFileName.append(fileName);
								stringBuilderFileName.append(System
										.currentTimeMillis());
								stringBuilderFileName.append(".jpg");
								stringBuilder.append(stringBuilderFileName);
								imageFromCamera = new File(
										stringBuilder.toString());
								FileOutputStream fos = new FileOutputStream(
										imageFromCamera);
								bm.compress(CompressFormat.JPEG, 100, fos);
								fos.flush();
								fos.close();

								mImageUri = Uri.fromFile(imageFromCamera);
								fileName = stringBuilderFileName.toString();

							} catch (FileNotFoundException e) {
								ShowUtils.getInstance().alertFileProblem(
										e.getMessage(), this);
							} catch (IOException e) {
								ShowUtils.getInstance().alertFileProblem(
										e.getMessage(), this);
							}

						}
					}

				} else {
					mImageUri = intent.getData();
				}
			}
			if (fileName.equals(SENT_WITH_REGALANDROID)) {
				fileName = AndroidUriUtils.extractFilenameFromUri(mImageUri, this);
			}
			filenameEditText.setText(fileName);
		}

		if ("android.intent.action.SEND_MULTIPLE".equals(intent.getAction())) {
			mImageUris = extras.getParcelableArrayList(Intent.EXTRA_STREAM);
			filenameEditText.setEnabled(false);
			filenameEditText.setText(R.string.multiple_files_upload);

		}

		sendButton = (Button) findViewById(R.id.send_button);
		cancelButton = (Button) findViewById(R.id.cancel_button);
		goToReGalAndroidButton = (Button) findViewById(R.id.regalandroid_button);
		sendButton.setOnClickListener(this);
		cancelButton.setOnClickListener(this);
		goToReGalAndroidButton.setOnClickListener(this);

		galleryUrlText = (TextView) findViewById(R.id.gallery_url);
		connectedAsUserText = (TextView) findViewById(R.id.connected_as_user);

	}

	@Override
	protected void onResume() {
		super.onResume();
		application = ((RegalAndroidApplication) getApplication());
		spinner = (Spinner) findViewById(R.id.album_list);
		spinner.setAdapter(albumAdapter);

		progressDialog = ProgressDialog.show(this,
				getString(R.string.please_wait),
				getString(R.string.connecting_to_the_gallery), true);
		new LoginTask(this, progressDialog, connectedAsUserText,
				galleryUrlText, sendButton).execute(
				Settings.getGalleryUrl(this), Settings.getUsername(this),
				Settings.getPassword(this));

	}

	/**
	 * This method is called back from LoginTask
	 */
	@SuppressWarnings("unchecked")
	public void showAlbumList() {
		// we recover the context from the database
		DBUtils.getInstance().recoverContextFromDatabase(this);
		Album currentAlbum = null;
		if (((RegalAndroidApplication) getApplication()).getCurrentAlbum() != null) {

			currentAlbum = AlbumUtils
					.findAlbumFromAlbumName(
							application
									.getCurrentAlbum(),
									application.getCurrentAlbum().getName());
		}
		progressDialog = ProgressDialog.show(this,
				getString(R.string.please_wait),
				getString(R.string.fetching_gallery_albums), true);
		new FetchAlbumForUploadTask(this, progressDialog, spinner, currentAlbum)
				.execute(Settings.getGalleryUrl(this));

	}

	@Override
	public void onClick(View v) {

		switch (v.getId()) {
		case R.id.send_button:

			Album selectAlbum = (Album) spinner.getSelectedItem();

			if (mImageUri == null && mImageUris == null) {
				// there is no image to send
				Toast.makeText(this, R.string.upload_photo_no_photo,
						Toast.LENGTH_LONG);
			} else {

				DBUtils.getInstance()
						.recoverContextFromDatabase(this);
				progressDialog = ProgressDialog.show(this,
						getString(R.string.please_wait),
						getString(R.string.adding_photo), true);

				// one picture to upload : can be from camera or from sdcard
				if (mImageUri != null) {
					new AddPhotoTask(this, progressDialog).execute(
							Settings.getGalleryUrl(this),
							Integer.valueOf(selectAlbum.getName()), mImageUri,
							false, filenameEditText.getText().toString(),
							imageFromCamera);
				}
				// multiple file upload
				else if (mImageUris != null) {
					new AddPhotosTask(this, progressDialog).execute(
							Settings.getGalleryUrl(this),
							Integer.valueOf(selectAlbum.getName()), mImageUris,
							false);
				}
			}

			break;
		case R.id.cancel_button:
			finish();
			break;
		case R.id.regalandroid_button:
			startActivity(new Intent(this, Settings.class));
			break;

		}

	}
}
