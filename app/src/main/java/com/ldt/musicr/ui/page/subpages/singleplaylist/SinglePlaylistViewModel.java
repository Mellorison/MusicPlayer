package com.ldt.musicr.ui.page.subpages.singleplaylist;

import android.content.Context;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.ldt.musicr.App;
import com.ldt.musicr.R;
import com.ldt.musicr.helper.AppExecutors;
import com.ldt.musicr.helper.Reliable;
import com.ldt.musicr.helper.ReliableEvent;
import com.ldt.musicr.loader.medialoader.LastAddedLoader;
import com.ldt.musicr.loader.medialoader.PlaylistSongLoader;
import com.ldt.musicr.loader.medialoader.TopAndRecentlyPlayedTracksLoader;
import com.ldt.musicr.model.Playlist;
import com.ldt.musicr.model.Song;
import com.ldt.musicr.ui.base.MPViewModel;
import com.ldt.musicr.ui.bottomsheet.SortOrderBottomSheet;
import com.ldt.musicr.ui.widget.rangeseekbar.Utils;
import com.ldt.musicr.util.AutoGeneratedPlaylistBitmap;
import com.ldt.musicr.util.SortOrder;
import com.ldt.musicr.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SinglePlaylistViewModel extends MPViewModel {
    public static class State {
        public Playlist mPlaylist;
        public String mTitle = "";
        public String mDescription = "";
        public final List<Song> songs = new ArrayList<>();
        public Bitmap mCoverImage = null;
        public int sortOrder = 0;
    }

    public MutableLiveData<ReliableEvent<State>> getStateLiveData() {
        return mStateLiveData;
    }

    private final MutableLiveData<ReliableEvent<State>> mStateLiveData = new MutableLiveData<>();

    public void refreshData() {
        AppExecutors.getInstance().diskIO().execute(() -> {
            ReliableEvent<State> event = mStateLiveData.getValue();
            final State state = event != null ? event.getReliable().getData() : null;

            Reliable<?> reliable = null;

            if (state == null || state.mPlaylist == null) {
                reliable = Reliable.failed(MESSAGE_CODE_INVALID_PARAMS, new IllegalArgumentException("Require playlist parameter"));
            }

            int order = 0;
            if (reliable == null) {
                if (Util.equals(ACTION_SET_PARAMS, event.getAction()) && Util.equals(state.mPlaylist.name, App.getInstance().getApplicationContext().getResources().getString(R.string.playlist_last_added))) {
                    order = 2;
                } else {
                    order = App.getInstance().getPreferencesUtility().getSharePreferences().getInt("sort_order_playlist_" + state.mPlaylist.name + "_" + state.mPlaylist.id, 0);
                }
            }

            List<Song> songs = null;
            if (reliable == null) {
                songs = getPlaylist(App.getInstance().getApplicationContext(), state.mPlaylist, SortOrderBottomSheet.mSortOrderCodes[order]);
                if (songs == null) {
                    reliable = Reliable.failed(MESSAGE_CODE_INVALID_RESPONSE, new NullPointerException("Null song playlist"));
                }
            }

            String title = "", description = "";
            if (reliable == null) {
                title = state.mPlaylist.name;
                ArrayList<String> artists = new ArrayList<>();
                for (int i = 0; i < songs.size() && artists.size() < 5; i++) {
                    Song song = songs.get(i);
                    if (!artists.contains(song.artistName)) artists.add(song.artistName);
                }
                description = TextUtils.join(" · ", artists);
            }

            // should we sets result here ?
            if (reliable == null) {
                ReliableEvent<State> latestEvent = mStateLiveData.getValue();
                final State latestState = latestEvent != null ? latestEvent.getReliable().getData() : null;

                if (latestState != null) {
                    latestState.mTitle = title;
                    latestState.mDescription = description;
                    latestState.songs.clear();
                    latestState.songs.addAll(songs);
                    latestState.sortOrder = order;
                    mStateLiveData.postValue(new ReliableEvent<>(Reliable.success(latestState), ACTION_RELOAD_DATA));
                }
            }

            // now load the cover bitmap
            Bitmap coverBitmap = null;
            if (reliable == null) {
                try {
                    coverBitmap = AutoGeneratedPlaylistBitmap.getBitmap(App.getInstance().getApplicationContext(), songs, false, false);
                    if (coverBitmap == null) {
                        reliable = Reliable.failed(MESSAGE_CODE_INVALID_RESPONSE, new NullPointerException("Cover bitmap is null"));
                    }
                } catch (Exception e) {
                    reliable = Reliable.failed(MESSAGE_CODE_INVALID_RESPONSE, e);
                }
            }

            // now post the bitmap result
            ReliableEvent<State> latestEvent = mStateLiveData.getValue();
            final State latestState = latestEvent != null ? latestEvent.getReliable().getData() : null;
            if (reliable != null) {
                Reliable<State> failedReliable = Reliable.custom(Reliable.Type.FAILED, latestState, reliable.mMessageCode, reliable.getMessage(), reliable.mThrowable);
                mStateLiveData.postValue(new ReliableEvent<State>(failedReliable, ACTION_RELOAD_DATA));
            } else {
                if (latestState != null) {
                    latestState.mTitle = title;
                    latestState.mDescription = description;
                    latestState.songs.clear();
                    latestState.songs.addAll(songs);
                    latestState.mCoverImage = coverBitmap;
                }
                Reliable<State> successReliable = Reliable.success(latestState);
                mStateLiveData.postValue(new ReliableEvent<>(successReliable, ACTION_RELOAD_DATA));

            }

        });
    }

    public static List<Song> getPlaylist(@NonNull Context context, Playlist list, String sortOrder) {
        if (Util.equals(list.name, context.getString(R.string.playlist_last_added)))
            return LastAddedLoader.getLastAddedSongs(context, sortOrder);
        else if (Util.equals(list.name, context.getString(R.string.playlist_recently_played))) {
            return TopAndRecentlyPlayedTracksLoader.getRecentlyPlayedTracks(context);
        } else if (Util.equals(list.name, context.getString(R.string.playlist_top_tracks))) {
            return TopAndRecentlyPlayedTracksLoader.getTopTracks(context);
        } else {
            List<Song> songlist = new ArrayList<>(PlaylistSongLoader.getPlaylistSongList(context, list.id));
            return songlist;
        }
    }
}
