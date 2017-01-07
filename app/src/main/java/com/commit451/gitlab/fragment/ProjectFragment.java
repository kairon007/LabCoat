package com.commit451.gitlab.fragment;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.commit451.gitlab.App;
import com.commit451.gitlab.R;
import com.commit451.gitlab.activity.ProjectActivity;
import com.commit451.gitlab.event.ProjectReloadEvent;
import com.commit451.gitlab.model.api.Project;
import com.commit451.gitlab.model.api.RepositoryFile;
import com.commit451.gitlab.model.api.RepositoryTreeObject;
import com.commit451.gitlab.navigation.Navigator;
import com.commit451.gitlab.rx.CustomSingleObserver;
import com.commit451.gitlab.rx.DecodeObservableFactory;
import com.commit451.gitlab.util.BypassImageGetterFactory;
import com.commit451.gitlab.util.InternalLinkMovementMethod;
import com.commit451.reptar.Result;
import com.jakewharton.retrofit2.adapter.rxjava2.HttpException;
import com.vdurmont.emoji.EmojiParser;

import org.greenrobot.eventbus.Subscribe;

import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import in.uncod.android.bypass.Bypass;
import io.reactivex.Single;
import io.reactivex.SingleSource;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import retrofit2.Response;
import timber.log.Timber;

/**
 * Shows the overview of the project
 */
public class ProjectFragment extends ButterKnifeFragment {

    private static final int README_TYPE_UNKNOWN = -1;
    private static final int README_TYPE_MARKDOWN = 0;
    private static final int README_TYPE_TEXT = 1;
    private static final int README_TYPE_HTML = 2;
    private static final int README_TYPE_NO_EXTENSION = 3;

    public static ProjectFragment newInstance() {
        return new ProjectFragment();
    }

    @BindView(R.id.swipe_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.creator)
    TextView mCreatorView;
    @BindView(R.id.star_count)
    TextView mStarCountView;
    @BindView(R.id.forks_count)
    TextView mForksCountView;
    @BindView(R.id.overview_text)
    TextView mOverviewVew;

    private Project mProject;
    private String mBranchName;
    private EventReceiver mEventReceiver;
    private Bypass mBypass;

    @OnClick(R.id.creator)
    void onCreatorClick() {
        if (mProject != null) {
            if (mProject.belongsToGroup()) {
                Navigator.navigateToGroup(getActivity(), mProject.getNamespace().getId());
            } else {
                Navigator.navigateToUser(getActivity(), mProject.getOwner());
            }
        }
    }

    @OnClick(R.id.root_fork)
    void onForkClicked() {
        if (mProject != null) {
            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.project_fork_title)
                    .setMessage(R.string.project_fork_message)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            App.get().getGitLab().forkProject(mProject.getId())
                                    .compose(ProjectFragment.this.<String>bindToLifecycle())
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(new CustomSingleObserver<String>() {

                                        @Override
                                        public void error(Throwable t) {
                                            Snackbar.make(mSwipeRefreshLayout, R.string.fork_failed, Snackbar.LENGTH_SHORT)
                                                    .show();
                                        }

                                        @Override
                                        public void success(String s) {
                                            Snackbar.make(mSwipeRefreshLayout, R.string.project_forked, Snackbar.LENGTH_SHORT)
                                                    .show();
                                        }
                                    });
                        }
                    })
                    .show();
        }
    }

    @OnClick(R.id.root_star)
    void onStarClicked() {
        if (mProject != null) {
            App.get().getGitLab().starProject(mProject.getId())
                    .compose(this.<Response<Project>>bindToLifecycle())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new CustomSingleObserver<Response<Project>>() {

                        @Override
                        public void error(Throwable t) {
                            if (t instanceof HttpException) {
                                if (((HttpException) t).response().code() == 304) {
                                    Snackbar.make(mSwipeRefreshLayout, R.string.project_already_starred, Snackbar.LENGTH_SHORT)
                                            .setAction(R.string.project_unstar, new View.OnClickListener() {
                                                @Override
                                                public void onClick(View v) {
                                                    unstarProject();
                                                }
                                            })
                                            .show();
                                    return;
                                }
                            }
                            Snackbar.make(mSwipeRefreshLayout, R.string.project_star_failed, Snackbar.LENGTH_SHORT)
                                    .show();
                        }

                        @Override
                        public void success(Response<Project> projectResponse) {
                            Snackbar.make(mSwipeRefreshLayout, R.string.project_starred, Snackbar.LENGTH_SHORT)
                                    .show();
                        }
                    });
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBypass = new Bypass(getActivity());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_project, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mEventReceiver = new EventReceiver();
        App.bus().register(mEventReceiver);

        mOverviewVew.setMovementMethod(new InternalLinkMovementMethod(App.get().getAccount().getServerUrl()));

        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                loadData();
            }
        });

        if (getActivity() instanceof ProjectActivity) {
            mProject = ((ProjectActivity) getActivity()).getProject();
            mBranchName = ((ProjectActivity) getActivity()).getRef();
            bindProject(mProject);
            loadData();
        } else {
            throw new IllegalStateException("Incorrect parent activity");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        App.bus().unregister(mEventReceiver);
    }

    @Override
    protected void loadData() {
        if (getView() == null) {
            return;
        }

        if (mProject == null || TextUtils.isEmpty(mBranchName)) {
            mSwipeRefreshLayout.setRefreshing(false);
            return;
        }

        mSwipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                if (mSwipeRefreshLayout != null) {
                    mSwipeRefreshLayout.setRefreshing(true);
                }
            }
        });

        final ReadmeResult result = new ReadmeResult();
        App.get().getGitLab().getTree(mProject.getId(), mBranchName, null)
                .flatMap(new Function<List<RepositoryTreeObject>, SingleSource<Result<RepositoryTreeObject>>>() {
                    @Override
                    public SingleSource<Result<RepositoryTreeObject>> apply(List<RepositoryTreeObject> repositoryTreeObjects) throws Exception {
                        for (RepositoryTreeObject treeItem : repositoryTreeObjects) {
                            if (getReadmeType(treeItem.getName()) != README_TYPE_UNKNOWN) {
                                return Single.just(new Result<>(treeItem));
                            }
                        }
                        return Single.just(Result.<RepositoryTreeObject>empty());
                    }
                })
                .flatMap(new Function<Result<RepositoryTreeObject>, SingleSource<Result<RepositoryFile>>>() {
                    @Override
                    public SingleSource<Result<RepositoryFile>> apply(Result<RepositoryTreeObject> repositoryTreeObjectResult) throws Exception {
                        if (repositoryTreeObjectResult.isPresent()) {
                            RepositoryFile repositoryFile = App.get().getGitLab().getFile(mProject.getId(), repositoryTreeObjectResult.get().getName(), mBranchName)
                                    .blockingGet();
                            result.repositoryFile = repositoryFile;
                            return Single.just(new Result<>(repositoryFile));
                        }
                        return Single.just(Result.<RepositoryFile>empty());
                    }
                })
                .flatMap(new Function<Result<RepositoryFile>, SingleSource<ReadmeResult>>() {
                    @Override
                    public SingleSource<ReadmeResult> apply(Result<RepositoryFile> repositoryFileResult) throws Exception {
                        if (repositoryFileResult.isPresent()) {
                            result.bytes = DecodeObservableFactory.newDecode(repositoryFileResult.get().getContent())
                                    .blockingGet();
                            return Single.just(result);
                        }
                        return Single.just(result);
                    }
                })
                .compose(this.<ReadmeResult>bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CustomSingleObserver<ReadmeResult>() {

                    @Override
                    public void error(Throwable t) {
                        Timber.e(t);
                        mSwipeRefreshLayout.setRefreshing(false);
                        mOverviewVew.setText(R.string.connection_error_readme);
                    }

                    @Override
                    public void success(ReadmeResult readmeResult) {
                        mSwipeRefreshLayout.setRefreshing(false);
                        if (result.repositoryFile != null && result.bytes != null) {
                            String text = new String(result.bytes);
                            switch (getReadmeType(result.repositoryFile.getFileName())) {
                                case README_TYPE_MARKDOWN:
                                    text = EmojiParser.parseToUnicode(text);
                                    mOverviewVew.setText(mBypass.markdownToSpannable(text,
                                            BypassImageGetterFactory.create(mOverviewVew,
                                                    App.get().getPicasso(),
                                                    App.get().getAccount().getServerUrl().toString(),
                                                    mProject)));
                                    break;
                                case README_TYPE_HTML:
                                    mOverviewVew.setText(Html.fromHtml(text));
                                    break;
                                case README_TYPE_TEXT:
                                    mOverviewVew.setText(text);
                                    break;
                                case README_TYPE_NO_EXTENSION:
                                    mOverviewVew.setText(text);
                                    break;
                            }
                        } else {
                            mOverviewVew.setText(R.string.no_readme_found);
                        }
                    }
                });
    }

    private void bindProject(Project project) {
        if (project == null) {
            return;
        }

        if (project.belongsToGroup()) {
            mCreatorView.setText(String.format(getString(R.string.created_by), project.getNamespace().getName()));
        } else {
            mCreatorView.setText(String.format(getString(R.string.created_by), project.getOwner().getUsername()));
        }
        mStarCountView.setText(String.valueOf(project.getStarCount()));
        mForksCountView.setText(String.valueOf(project.getForksCount()));
    }

    private int getReadmeType(String filename) {
        switch (filename.toLowerCase()) {
            case "readme.md":
                return README_TYPE_MARKDOWN;
            case "readme.html":
            case "readme.htm":
                return README_TYPE_HTML;
            case "readme.txt":
                return README_TYPE_TEXT;
            case "readme":
                return README_TYPE_NO_EXTENSION;
        }
        return README_TYPE_UNKNOWN;
    }

    private void unstarProject() {
        App.get().getGitLab().unstarProject(mProject.getId())
                .compose(this.<Project>bindToLifecycle())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new CustomSingleObserver<Project>() {

                    @Override
                    public void error(Throwable t) {
                        Snackbar.make(mSwipeRefreshLayout, R.string.unstar_failed, Snackbar.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void success(Project project) {
                        Snackbar.make(mSwipeRefreshLayout, com.commit451.gitlab.R.string.project_unstarred, Snackbar.LENGTH_SHORT)
                                .show();
                    }
                });
    }

    private static class ReadmeResult {
        byte[] bytes;
        RepositoryFile repositoryFile;
    }

    private class EventReceiver {
        @Subscribe
        public void onProjectReload(ProjectReloadEvent event) {
            mProject = event.mProject;
            mBranchName = event.mBranchName;
            loadData();
        }
    }
}
