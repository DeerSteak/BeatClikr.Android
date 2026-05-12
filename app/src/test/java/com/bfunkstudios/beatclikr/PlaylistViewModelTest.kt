package com.bfunkstudios.beatclikr

import com.bfunkstudios.beatclikr.data.Playlist
import com.bfunkstudios.beatclikr.data.PlaylistRepository
import com.bfunkstudios.beatclikr.data.PlaylistWithEntries
import com.bfunkstudios.beatclikr.data.SongRepository
import com.bfunkstudios.beatclikr.ui.PlaylistViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private lateinit var repository: PlaylistRepository
    private lateinit var songRepository: SongRepository
    private lateinit var viewModel: PlaylistViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk(relaxed = true)
        songRepository = mockk(relaxed = true)
        every { repository.getAllPlaylists() } returns flowOf(emptyList())
        every { songRepository.getAllSongs() } returns flowOf(emptyList())
        viewModel = PlaylistViewModel(repository, songRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `new playlist draft updates and clears`() {
        viewModel.updateNewPlaylistName("Set 1")

        assertEquals("Set 1", viewModel.newPlaylistName)

        viewModel.clearNewPlaylistDraft()

        assertEquals("", viewModel.newPlaylistName)
    }

    @Test
    fun `createPlaylistFromDraft creates playlist and clears draft`() {
        val playlist = Playlist(name = "Set 1")
        coEvery { repository.createPlaylist("Set 1") } returns playlist
        viewModel.updateNewPlaylistName("Set 1")

        viewModel.createPlaylistFromDraft()

        coVerify { repository.createPlaylist("Set 1") }
        assertEquals("", viewModel.newPlaylistName)
    }

    @Test
    fun `rename draft starts updates and cancels`() {
        val playlist = PlaylistWithEntries(playlist = Playlist(name = "Old"), entries = emptyList())

        viewModel.beginRenamePlaylist(playlist)

        assertSame(playlist, viewModel.playlistToRename)
        assertEquals("Old", viewModel.renamePlaylistName)

        viewModel.updateRenamePlaylistName("New")
        assertEquals("New", viewModel.renamePlaylistName)

        viewModel.cancelRenamePlaylist()
        assertNull(viewModel.playlistToRename)
        assertEquals("", viewModel.renamePlaylistName)
    }

    @Test
    fun `confirmRenamePlaylist renames selected playlist and clears draft`() {
        val playlist = PlaylistWithEntries(playlist = Playlist(name = "Old"), entries = emptyList())
        viewModel.beginRenamePlaylist(playlist)
        viewModel.updateRenamePlaylistName("New")

        viewModel.confirmRenamePlaylist()

        coVerify { repository.renamePlaylist(playlist.playlist, "New") }
        assertNull(viewModel.playlistToRename)
        assertEquals("", viewModel.renamePlaylistName)
    }
}
