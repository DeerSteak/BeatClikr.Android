package com.bfunkstudios.beatclikr.data

import com.bfunkstudios.beatclikr.data.db.SongDao
import com.bfunkstudios.beatclikr.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

class SongRepositoryImpl @Inject constructor(
    private val dao: SongDao,
    @param:ApplicationScope private val appScope: CoroutineScope
) : SongRepository {

    init {
        appScope.launch {
            if (dao.getCount() == 0) {
                DataSource.seedSongs.forEach { dao.upsert(it) }
            }
        }
    }

    override fun getAllSongs(): Flow<List<Song>> = dao.getAllSongs()
    override suspend fun upsert(song: Song) = dao.upsert(song)
    override suspend fun delete(song: Song) = dao.delete(song)
}
