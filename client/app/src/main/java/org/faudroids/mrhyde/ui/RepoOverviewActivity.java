package org.faudroids.mrhyde.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.google.common.base.Optional;
import com.squareup.picasso.Picasso;

import org.faudroids.mrhyde.R;
import org.faudroids.mrhyde.app.MrHydeApp;
import org.faudroids.mrhyde.git.AvatarPlaceholder;
import org.faudroids.mrhyde.git.GitManager;
import org.faudroids.mrhyde.git.RepositoriesManager;
import org.faudroids.mrhyde.git.Repository;
import org.faudroids.mrhyde.jekyll.AbstractJekyllContent;
import org.faudroids.mrhyde.jekyll.Draft;
import org.faudroids.mrhyde.jekyll.JekyllManager;
import org.faudroids.mrhyde.jekyll.JekyllManagerFactory;
import org.faudroids.mrhyde.jekyll.Post;
import org.faudroids.mrhyde.ui.utils.AbstractActivity;
import org.faudroids.mrhyde.ui.utils.JekyllUiUtils;
import org.faudroids.mrhyde.ui.utils.ObservableScrollView;
import org.faudroids.mrhyde.utils.DefaultErrorAction;
import org.faudroids.mrhyde.utils.DefaultTransformer;
import org.faudroids.mrhyde.utils.ErrorActionBuilder;
import org.faudroids.mrhyde.utils.HideSpinnerAction;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;

public final class RepoOverviewActivity extends AbstractActivity {

  public static final String EXTRA_REPOSITORY = "EXTRA_REPOSITORY";

  private static final int
      REQUEST_SHOW_LIST = 42,
      REQUEST_SHOW_ALL_FILES = 43;

  @BindView(R.id.scroll_view) protected ObservableScrollView scrollView;
  @BindView(R.id.image_overview_background) protected ImageView overviewBackgroundImage;
  @BindView(R.id.image_repo_owner) protected ImageView repoOwnerImage;
  @BindView(R.id.text_post_count) protected TextView postDraftCountView;
  @BindView(R.id.button_favourite) protected ImageButton favouriteButton;
  private Drawable actionBarDrawable;

  @Inject JekyllUiUtils jekyllUiUtils;
  @BindView(R.id.header_posts) protected View postsHeader;
  @BindView(R.id.list_posts) protected ListView postsListView;
  private PostsListAdapter postsListAdapter;
  @BindView(R.id.item_no_posts) protected View noPostsView;

  @BindView(R.id.card_drafts) protected View draftsCard;
  @BindView(R.id.header_drafts) protected View draftsHeader;
  @BindView(R.id.list_drafts) protected ListView draftsListView;
  private DraftsListAdapter draftsListAdapter;

  @BindView(R.id.card_all_files) protected View allFilesView;

  @BindView(R.id.add) protected FloatingActionsMenu addButton;
  @BindView(R.id.add_post) protected FloatingActionButton addPostButton;
  @BindView(R.id.add_draft) protected FloatingActionButton addDraftButton;
  @BindView(R.id.tint) protected View tintView;

  private Repository repository;
  @Inject JekyllManagerFactory jekyllManagerFactory;
  private JekyllManager jekyllManager;
  @Inject RepositoriesManager repositoriesManager;
  private GitManager gitManager;

  @Inject ActivityIntentFactory intentFactory;

  private GitActionBarMenu gitActionBarMenu;


  @Override
  public void onCreate(final Bundle savedInstanceState) {
    ((MrHydeApp) getApplication()).getComponent().inject(this);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_repo_overview);
    ButterKnife.bind(this);

    // get arguments
    repository = (Repository) this.getIntent().getSerializableExtra(EXTRA_REPOSITORY);
    gitManager = repositoriesManager.openRepository(repository);
    jekyllManager = jekyllManagerFactory.createJekyllManager(gitManager);
    setTitle(repository.getName());

    // check for empty repos
    if (!this.assertRepoNotEmpty()) return;

    // setup posts lists
    postsListAdapter = new PostsListAdapter(this);
    postsListView.setAdapter(postsListAdapter);

    // setup drafts lists
    draftsListAdapter = new DraftsListAdapter(this);
    draftsListView.setAdapter(draftsListAdapter);

    // load content
    showSpinner();
    loadJekyllContent();

    // setup posts clicks
    postsHeader.setOnClickListener(
        v -> startActivityForResult(intentFactory.createPostsIntent(repository), REQUEST_SHOW_LIST)
    );

    // setup drafts clicks
    draftsHeader.setOnClickListener(
        v -> startActivityForResult(intentFactory.createDraftsIntent(repository), REQUEST_SHOW_LIST)
    );

    // setup all files card
    allFilesView.setOnClickListener(v -> {
      Intent intent = new Intent(RepoOverviewActivity.this, DirActivity.class);
      intent.putExtra(DirActivity.EXTRA_REPOSITORY, repository);
      startActivityForResult(intent, REQUEST_SHOW_ALL_FILES);
    });

    // setup add buttons
    addButton.setOnFloatingActionsMenuUpdateListener(new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener() {
      @Override
      public void onMenuExpanded() {
        tintView.animate().alpha(1).setDuration(200).start();
      }

      @Override
      public void onMenuCollapsed() {
        tintView.animate().alpha(0).setDuration(200).start();
      }
    });
    addPostButton.setOnClickListener(v -> {
      addButton.collapse();
      jekyllUiUtils.showNewPostDialog(
          RepoOverviewActivity.this,
          jekyllManager,
          repository,
          Optional.<File>absent(),
          post -> loadJekyllContent()
      );
    });
    addDraftButton.setOnClickListener(v -> {
      addButton.collapse();
      jekyllUiUtils.showNewDraftDialog(
          RepoOverviewActivity.this,
          jekyllManager,
          repository,
          Optional.<File>absent(),
          draft -> loadJekyllContent());
    });
    tintView.setOnClickListener(v -> addButton.collapse());

    // load owner image
    Picasso.with(this)
        .load(repository.getOwner().get().getAvatarUrl().orNull())
        .resizeDimen(R.dimen.overview_owner_icon_size_max, R.dimen.overview_owner_icon_size_max)
        .placeholder(repository.accept(new AvatarPlaceholder(), null))
        .into(repoOwnerImage);

    // setup scroll partially hides top image
    actionBarDrawable = new ColorDrawable(getResources().getColor(R.color.colorPrimary));
    getSupportActionBar().setBackgroundDrawable(actionBarDrawable);
    scrollView.setOnScrollListener((scrollView1, l, t, oldL, oldT) -> RepoOverviewActivity.this.onScrollChanged());

    // setup favourite button
    if (repository.isFavorite()) {
      favouriteButton.setSelected(true);
    }
    favouriteButton.setOnClickListener(v -> {
      compositeSubscription.add(repositoriesManager
          .setRepositoryFavoriteStatus(repository, !repository.isFavorite())
          .compose(new DefaultTransformer<>())
          .subscribe(
              updatedRepository -> {
                repository = updatedRepository;
                favouriteButton.setSelected(repository.isFavorite());
                Toast.makeText(
                    RepoOverviewActivity.this,
                    getString(repository.isFavorite() ? R.string.marked_toast : R.string.unmarked_toast),
                    Toast.LENGTH_SHORT
                ).show();
              }, new ErrorActionBuilder()
                  .add(new DefaultErrorAction(RepoOverviewActivity.this, "failed to set favorite status"))
                  .add(new HideSpinnerAction(RepoOverviewActivity.this))
                  .build()));
    });

    // git related menu items
    gitActionBarMenu = new GitActionBarMenu(
        this,
        this::loadJekyllContent,
        gitManager,
        repository,
        intentFactory
    );
  }


  private void loadJekyllContent() {
    compositeSubscription.add(Observable.zip(
        jekyllManager.getAllPosts(),
        jekyllManager.getAllDrafts(),
        JekyllContent::new)
        .compose(new DefaultTransformer<JekyllContent>())
        .subscribe(jekyllContent -> {
          if (isSpinnerVisible()) hideSpinner();
          actionBarDrawable.setAlpha(0); // delay until spinner is hidden
          invalidateOptionsMenu(); // re-enable options menu

          // setup header
          postDraftCountView.setText(getString(
              R.string.post_darft_count,
              getResources().getQuantityString(R.plurals.posts_count, jekyllContent.posts.size(), jekyllContent.posts.size()),
              getResources().getQuantityString(R.plurals.drafts_count, jekyllContent.drafts.size(), jekyllContent.drafts.size())));

          // setup cards
          setupFirstThreeEntries(jekyllContent.posts, postsListAdapter);
          setupFirstThreeEntries(jekyllContent.drafts, draftsListAdapter);

          // setup empty views
          if (!jekyllContent.posts.isEmpty()) noPostsView.setVisibility(View.GONE);
          else noPostsView.setVisibility(View.VISIBLE);
          if (jekyllContent.drafts.isEmpty()) draftsCard.setVisibility(View.GONE);
          else draftsCard.setVisibility(View.VISIBLE);

          // refresh action bar background drawable
          onScrollChanged();

        }, new ErrorActionBuilder()
            .add(new DefaultErrorAction(RepoOverviewActivity.this, "failed to load posts"))
            .add(new HideSpinnerAction(RepoOverviewActivity.this))
            .build()));
  }


  // updates action bar and repo image during scroll
  private void onScrollChanged() {
    final int headerHeight = overviewBackgroundImage.getHeight() - getSupportActionBar().getHeight();
    // comparison with zero fixes race condition between UI specs setup and measuring them
    final float ratio = headerHeight == 0
        ? 0
        : (float) Math.min(Math.max(scrollView.getScrollY(), 0), headerHeight) / headerHeight;

    final int newAlpha = (int) (ratio * 255);
    actionBarDrawable.setAlpha(newAlpha);

    // resize owner icon
    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) repoOwnerImage.getLayoutParams();
    float minSize = getResources().getDimension(R.dimen.overview_owner_icon_size_min);
    float maxSize = getResources().getDimension(R.dimen.overview_owner_icon_size_max);
    float minLeftMargin = getResources().getDimension(R.dimen.overview_owner_icon_margin_left);
    float minTopMargin = getResources().getDimension(R.dimen.overview_owner_icon_margin_top);
    float topMarginAddition = getResources().getDimension(R.dimen.overview_owner_icon_margin_top_addition);
    float size = (minSize + (maxSize - minSize) * (1 - ratio));
    params.height = (int) size;
    params.width = (int) size;
    params.leftMargin = (int) (minLeftMargin + (maxSize - size) / 2); // keep left margin stable
    params.topMargin = (int) (minTopMargin + (maxSize - size) + topMarginAddition * ratio); // moves icon down while resizing
    repoOwnerImage.setLayoutParams(params);
  }


  private <T> void setupFirstThreeEntries(List<T> items, ArrayAdapter<T> listAdapter) {
    // get first 3 posts
    List<T> firstItems = new ArrayList<>();
    for (int i = 0; i < 3 && i < items.size(); ++i) {
      firstItems.add(items.get(i));
    }
    listAdapter.clear();
    listAdapter.addAll(firstItems);
    listAdapter.notifyDataSetChanged();
  }


  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      // update posts / drafts list (might have changed)
      case REQUEST_SHOW_LIST:
      case REQUEST_SHOW_ALL_FILES:
        loadJekyllContent();
        break;
    }
  }


  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.menu_repo_overview, menu);
    return true;
  }


  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    // hide menu during loading
    if (isSpinnerVisible()) {
      menu.findItem(R.id.action_commit).setVisible(false);
      menu.findItem(R.id.action_preview).setVisible(false);
      menu.findItem(R.id.action_delete_repo).setVisible(false);
    }
    return super.onPrepareOptionsMenu(menu);
  }


  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.action_delete_repo:
        new MaterialDialog.Builder(this)
            .title(R.string.delete_repo_title)
            .content(R.string.delete_repo_message)
            .positiveText(R.string.delete_repo_confirm)
            .negativeText(android.R.string.cancel)
            .onPositive((dialog, which) -> {
              showSpinner();
              repositoriesManager
                  .deleteRepository(gitManager)
                  .compose(new DefaultTransformer<>())
                  .subscribe(
                      aVoid -> finish(),
                      new ErrorActionBuilder()
                          .add(new DefaultErrorAction(RepoOverviewActivity.this, "Failed to delete repo"))
                          .add(new HideSpinnerAction(RepoOverviewActivity.this))
                          .build()
                  );
            })
            .show();
        return true;
    }

    if (gitActionBarMenu.onOptionsItemSelected(item)) return true;

    return super.onOptionsItemSelected(item);
  }


  /**
   * @return true if the repo is NOT empty, false otherwise.
   */
  private boolean assertRepoNotEmpty() {
    // check for empty repository
    File rootDir = gitManager.getRepository().getRootDir();
    if (rootDir.listFiles().length > 1
        || !(rootDir.listFiles().length == 1 && rootDir.listFiles()[0].getName().equals(".git"))) {
      return true;
    }

    new MaterialDialog.Builder(this)
        .title(R.string.error_empty_repo_title)
        .content(R.string.error_empty_repo_message)
        .positiveText(android.R.string.ok)
        .onAny((dialog, which) -> finish())
        .show();
    return false;
  }


  private abstract class AbstractListAdapter<T extends AbstractJekyllContent> extends ArrayAdapter<T> {

    private final int viewResource;

    public AbstractListAdapter(Context context, int viewResource) {
      super(context, viewResource);
      this.viewResource = viewResource;
    }


    public View getView(int position, View convertView, ViewGroup parent) {
      // get item + view
      final T item = getItem(position);
      LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      View view = inflater.inflate(viewResource, parent, false);

      // setup click to edit
      view.setOnClickListener(
          v -> startActivity(intentFactory.createTextEditorIntent(repository, item.getFile(), false))
      );

      doGetView(view, item);
      return view;
    }

    protected abstract void doGetView(View view, T item);
  }


  /**
   * List adapter for displaying {@link org.faudroids.mrhyde.jekyll.Post}s.
   */
  private class PostsListAdapter extends AbstractListAdapter<Post> {

    public PostsListAdapter(Context context) {
      super(context, R.layout.item_overview_post);
    }

    @Override
    protected void doGetView(View view, Post post) {
      jekyllUiUtils.setPostOverview(view, post);
    }
  }


  /**
   * List adapter for displaying {@link org.faudroids.mrhyde.jekyll.Draft}s.
   */
  private class DraftsListAdapter extends AbstractListAdapter<Draft> {

    public DraftsListAdapter(Context context) {
      super(context, R.layout.item_overview_draft);
    }

    @Override
    protected void doGetView(View view, Draft draft) {
      jekyllUiUtils.setDraftOverview(view, draft);
    }
  }


  /**
   * Container class for holding all loaded Jekyll content
   */
  private static class JekyllContent {

    private final List<Post> posts;
    private final List<Draft> drafts;

    public JekyllContent(List<Post> posts, List<Draft> drafts) {
      this.posts = posts;
      this.drafts = drafts;
    }

  }

}
