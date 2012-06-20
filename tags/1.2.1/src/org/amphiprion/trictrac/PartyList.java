/*
 * @copyright 2010 Gerald Jacobson
 * @license GNU General Public License
 * 
 * This file is part of MyTricTrac.
 *
 * MyTricTrac is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyTricTrac is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with My Accounts.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.amphiprion.trictrac;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.amphiprion.trictrac.dao.PartyDao;
import org.amphiprion.trictrac.entity.Game;
import org.amphiprion.trictrac.entity.Party;
import org.amphiprion.trictrac.entity.PartyForList;
import org.amphiprion.trictrac.task.ITaskListener;
import org.amphiprion.trictrac.task.LoadPartiesTask;
import org.amphiprion.trictrac.task.SynchronizePartiesTask;
import org.amphiprion.trictrac.task.LoadPartiesTask.LoadPartyListener;
import org.amphiprion.trictrac.view.MyScrollView;
import org.amphiprion.trictrac.view.PartySummaryView;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.ViewTreeObserver.OnScrollChangedListener;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.LinearLayout.LayoutParams;

/**
 * @author amphiprion
 * 
 */
public class PartyList extends Activity implements LoadPartyListener {
	private static final int PAGE_SIZE = 20;

	private int loadedPage;
	private List<PartyForList> parties;
	private MyScrollView scrollView;
	private boolean allLoaded;
	private boolean loading;
	private String lastGameName = null;
	private Game game;
	private PartyForList current;
	private LoadPartiesTask task;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.party_list);

		game = (Game) getIntent().getSerializableExtra("GAME");
		if (game == null) {
			setTitle(getResources().getString(R.string.my_parties));
		} else {
			setTitle(getResources().getString(R.string.my_parties, game.getName()));
		}

		final Rect r = new Rect();
		scrollView = (MyScrollView) findViewById(R.id.scroll_view);
		scrollView.setOnScrollChanged(new OnScrollChangedListener() {
			@Override
			public void onScrollChanged() {
				if (!allLoaded && !loading) {
					LinearLayout ln = ((LinearLayout) scrollView.getChildAt(0));
					if (ln.getChildCount() > 3) {
						boolean b = ln.getChildAt(ln.getChildCount() - 3).getLocalVisibleRect(r);
						if (b) {
							loading = true;
							loadNextPage();
						}
					}
				}
			}
		});

		init();

	}

	private void init() {
		loadedPage = 0;
		if (parties == null) {
			parties = new ArrayList<PartyForList>();
		} else {
			parties.clear();
		}
		loadNextPage();
	}

	private void loadNextPage() {
		if (loadedPage == 0) {
			// int nb = GameDao.getInstance(this).getGameCount(collection,
			// search, query);
			// Toast.makeText(this,
			// getResources().getString(R.string.message_nb_result, nb),
			// Toast.LENGTH_LONG).show();
			List<PartyForList> newParties = PartyDao.getInstance(this).getParties(game, loadedPage, PAGE_SIZE);
			importEnded(true, newParties);
		} else {
			task = new LoadPartiesTask(this, game, loadedPage, PAGE_SIZE);
			task.execute();
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		if (game != null) {
			MenuItem addAccount = menu.add(0, ApplicationConstants.MENU_ID_CREATE_PARTY, 0, R.string.add_party);
			addAccount.setIcon(android.R.drawable.ic_menu_add);
		}

		MenuItem searchTrictrac = menu.add(0, ApplicationConstants.MENU_ID_SEARCH_TRICTRAC_GAME, 1,
				R.string.menu_search_trictrac);
		searchTrictrac.setIcon(android.R.drawable.ic_menu_search);

		MenuItem synchParty = menu.add(1, ApplicationConstants.MENU_ID_SYNCH_PARTY, 2, R.string.synch_parties);
		synchParty.setIcon(android.R.drawable.ic_menu_share);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == ApplicationConstants.MENU_ID_CREATE_PARTY) {
			Intent i = new Intent(this, EditParty.class);
			i.putExtra("GAME", game);
			// i.putExtra("PARTY", current);
			startActivityForResult(i, ApplicationConstants.ACTIVITY_RETURN_CREATE_PARTY);
		} else if (item.getItemId() == ApplicationConstants.MENU_ID_SEARCH_TRICTRAC_GAME) {
			Intent i = new Intent(this, TricTracGameList.class);
			startActivityForResult(i, ApplicationConstants.ACTIVITY_RETURN_SEARCH_TRICTRAC_GAME);
		} else if (item.getItemId() == ApplicationConstants.MENU_ID_SYNCH_PARTY) {
			SharedPreferences pref = getSharedPreferences(ApplicationConstants.GLOBAL_PREFERENCE, 0);
			long time = pref.getLong("SYNCH_PARTY_DATE", new Date(100, 0, 1).getTime());

			OnDateSetListener l = new OnDateSetListener() {
				@Override
				public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
					Date date = new Date(year - 1900, monthOfYear, dayOfMonth);
					SynchronizePartiesTask task = new SynchronizePartiesTask(new ITaskListener() {

						@Override
						public void taskEnded(boolean success) {
							if (success) {
								SharedPreferences pref = getSharedPreferences(ApplicationConstants.GLOBAL_PREFERENCE, 0);
								Editor editor = pref.edit();
								editor.putLong("SYNCH_PARTY_DATE", new Date().getTime());
								editor.commit();
								init();
							}

						}

						@Override
						public Context getContext() {
							return PartyList.this;
						}
					});
					task.execute(date);
				}
			};

			Date date = new Date(time);
			DatePickerDialog dlg = new DatePickerDialog(getContext(), l, date.getYear() + 1900, date.getMonth(), date
					.getDate());
			dlg.setTitle(getResources().getText(R.string.synch_start_date));
			dlg.show();
		}
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == RESULT_OK) {
			if (requestCode == ApplicationConstants.ACTIVITY_RETURN_CREATE_PARTY) {
				Party party = (Party) data.getSerializableExtra("PARTY");
				party.setLastUpdateDate(new Date());
				PartyDao.getInstance(this).persist(party);
				init();
			} else if (requestCode == ApplicationConstants.ACTIVITY_RETURN_SEARCH_TRICTRAC_GAME) {
				init();
			} else if (requestCode == ApplicationConstants.ACTIVITY_RETURN_UPDATE_PARTY) {
				Party party = (Party) data.getSerializableExtra("PARTY");
				party.setLastUpdateDate(new Date());
				PartyDao.getInstance(this).persist(party);
				init();
			}
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		menu.clear();
		if (v instanceof PartySummaryView) {
			current = ((PartySummaryView) v).getParty();
			menu.add(1, ApplicationConstants.MENU_ID_EDIT_PARTY, 0, R.string.edit_party);
			menu.add(2, ApplicationConstants.MENU_ID_DELETE_PARTY, 1, R.string.delete_party);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getItemId() == ApplicationConstants.MENU_ID_EDIT_PARTY) {
			Intent i = new Intent(this, EditParty.class);
			i.putExtra("GAME", game);
			Party party = PartyDao.getInstance(this).getParty(current.getId());
			i.putExtra("PARTY", party);
			startActivityForResult(i, ApplicationConstants.ACTIVITY_RETURN_UPDATE_PARTY);
		} else if (item.getItemId() == ApplicationConstants.MENU_ID_DELETE_PARTY) {
			Party party = PartyDao.getInstance(this).getParty(current.getId());
			PartyDao.getInstance(this).delete(party);
			init();
		}
		return true;
	}

	private void buildList() {
		LinearLayout ln = (LinearLayout) findViewById(R.id.party_list);
		ln.removeAllViews();
		if (parties.size() > 0) {
			addElementToList(parties);
		} else {
			TextView tv = new TextView(this);
			tv.setText(R.string.empty_party_list);
			ln.addView(tv);
		}
	}

	private void addElementToList(List<PartyForList> newParties) {
		LinearLayout ln = (LinearLayout) findViewById(R.id.party_list);
		if (newParties != parties) {
			parties.addAll(newParties);
			if (ln.getChildCount() > 0) {
				ln.removeViewAt(ln.getChildCount() - 1);
			}
		}
		for (final PartyForList party : newParties) {
			if (game == null && !party.getGameName().equals(lastGameName)) {
				lastGameName = party.getGameName();
				TextView txt = new TextView(this);
				txt.setBackgroundColor(getResources().getColor(R.color.black));
				txt.setText(party.getGameName());
				ln.addView(txt);
			}
			PartySummaryView view = new PartySummaryView(this, party);

			view.setOnLongClickListener(new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					registerForContextMenu(v);
					openContextMenu(v);
					unregisterForContextMenu(v);
					return true;
				}
			});

			ln.addView(view);
		}

		if (!allLoaded) {
			LinearLayout lnExpand = new LinearLayout(this);
			LayoutParams lp = new LayoutParams(android.view.ViewGroup.LayoutParams.FILL_PARENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			lnExpand.setLayoutParams(lp);
			ImageView im = new ImageView(this);
			LayoutParams imglp = new LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			imglp.gravity = Gravity.CENTER_VERTICAL;
			imglp.rightMargin = 5;
			im.setLayoutParams(imglp);

			im.setImageDrawable(getResources().getDrawable(R.drawable.loading));
			lnExpand.addView(im);

			LinearLayout accountLayout = new LinearLayout(this);
			LayoutParams aclp = new LayoutParams(android.view.ViewGroup.LayoutParams.FILL_PARENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 3);
			accountLayout.setLayoutParams(aclp);

			TextView tv = new TextView(this);
			tv.setText(getResources().getText(R.string.loading));
			LayoutParams tlp = new LayoutParams(android.view.ViewGroup.LayoutParams.FILL_PARENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			tv.setLayoutParams(tlp);
			accountLayout.addView(tv);
			lnExpand.addView(accountLayout);

			ln.addView(lnExpand);
			Animation a = new RotateAnimation(0, 360, 23.5f, 23.5f);
			a.setInterpolator(new LinearInterpolator());
			a.setRepeatCount(Animation.INFINITE);
			a.setDuration(2000);
			im.startAnimation(a);
		}
	}

	@Override
	public Context getContext() {
		return this;
	}

	@Override
	public void importEnded(boolean succeed, List<PartyForList> newParties) {
		if (succeed) {
			task = null;
			if (newParties != null && newParties.size() > 0) {
				if (newParties.size() == PAGE_SIZE + 1) {
					newParties.remove(PAGE_SIZE);
					allLoaded = false;
				} else {
					allLoaded = true;
				}
			} else {
				allLoaded = true;
			}
			if (loadedPage != 0) {
				addElementToList(newParties);
			} else {
				parties = newParties;
				buildList();
			}
			loadedPage++;
		}
		loading = false;
	}

}