package rs.racic.onelytics;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class GaJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            Refresher.refresh(GaJobService.this);
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
