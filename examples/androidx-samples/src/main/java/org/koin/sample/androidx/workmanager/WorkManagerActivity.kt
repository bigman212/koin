package org.koin.sample.androidx.workmanager

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import kotlinx.android.synthetic.main.workmanager_activity.*
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import org.koin.sample.android.R
import org.koin.sample.androidx.sdk.HostActivity
import org.koin.sample.androidx.utils.navigateTo
import org.koin.sample.androidx.workmanager.SimpleWorker.Companion.createData

/**
 * @author : Fabio de Matos
 * @since : 16/02/2020
 **/
class WorkManagerActivity : AppCompatActivity() {

    private val workManager = WorkManager.getInstance(this)
    private val service1: SimpleWorkerService by inject()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // pre-setup: preparing UI
        setContentView(R.layout.workmanager_activity)
        title = "Android Work Manager"
        workmanager_message.text = "Work Manager is starting."
        workmanager_button.setOnClickListener {
            navigateTo<HostActivity>(isRoot = true)
        }
        workmanager_button.isEnabled = false

        runWorkers()
    }

    private fun runWorkers() {
        CoroutineScope(Dispatchers.Default)
            .launch {
                assert(service1.isEmpty())

                // start test
                enqueuesWorkers()

                // verify it worked
                assertResponses(timeoutMs = 5_000)
            }
    }

    /**
     * Waits for [service1]'s channel for up to [timeoutMs].
     *
     * If channel doesn't contain 2 items by then, throws exception and crashes app.
     *
     */
    @SuppressLint("SetTextI18n")
    private suspend fun assertResponses(timeoutMs: Long) {
        try {
            withTimeout(timeoutMs) {
                service1.popAnswer()
                    .let {
                        assert(SimpleWorker.answer1st == it)
                    }

                service1.popAnswer()
                    .let {
                        assert(SimpleWorker.answer2nd == it)
                    }

                service1.isEmpty()
            }
        } catch (e: CancellationException) {
            throw RuntimeException(e) // taking too long to receive 42 means WorkManager failed somehow
        }

        withContext(Dispatchers.Main) {
            workmanager_message.text = "Work Manager completed!"
            workmanager_button.isEnabled = true
        }
    }

    private suspend fun enqueuesWorkers() {
        WorkManager.getInstance(this@WorkManagerActivity)
            .cancelAllWork()
            .result
            .await()

        enqueueWork<SimpleWorker>(createData(42))
        enqueueWork<SimpleWorker>(createData(43))
    }

    /**
     * @param T ListenableWorker that will perform work
     * @param answer the data fed into T when it's about to begin its work
     */
    private inline fun <reified T : ListenableWorker> enqueueWork(data: Data): OneTimeWorkRequest {

        val workName = SimpleWorker::class.simpleName + data.keyValueMap.getValue(SimpleWorker.KEY_ANSWER)

        return OneTimeWorkRequestBuilder<T>()
            .setInputData(data)
            .build()
            .also {
                workManager
                    .enqueueUniqueWork(
                        workName,
                        ExistingWorkPolicy.APPEND,
                        it
                    )
            }
    }
}