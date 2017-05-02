package com.example.mobile.typinganalyzer;


import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.inputmethodservice.Keyboard;
import android.support.annotation.NonNull;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static android.os.SystemClock.elapsedRealtime;
import static java.lang.StrictMath.abs;

public class KeyboardAnalyzer {
    private String curWord;
    private ArrayList<Long> curTimings;
    private Set<String> wordSet;
    private TimeProfileDbHelper dbHelper;
    private SharedPreferences prefs;
    private long timer;
    private double score;
    private Context context;
    private String scoreID = "score";
    private final double SCORE_THRESHOLD = 20;
    private final double COUNT_THRESHOLD = 5;
    private final double STDDEV_THRESHOLD = 2;


    public KeyboardAnalyzer(Context context) {
        this.context = context;
        curWord = new String();
        curTimings = new ArrayList<>();
        dbHelper = new TimeProfileDbHelper(context);
        prefs = context.getSharedPreferences(context.getString(R.string.prefs_key), Context.MODE_PRIVATE);
        score = Double.valueOf(prefs.getString(scoreID, "0"));
        wordSet = new HashSet<>();
        timer = elapsedRealtime();

        // put all the words in a set for quick access later
        String[] wordList = context.getResources().getStringArray(R.array.words);
        for (int i = 0; i < wordList.length; ++i) {
            wordSet.add(wordList[i]);
        }
    }

    /* The only function needed for interfacing with this class. This function take a character
     * (presumably the next character in the text) and the duration of the key press. Alphanumeric,
     * space, and backspace are only considered; everything else is ignored for simplicity. Words
     * are delimited with space and backspace. Once a word is formed, it is pushed on for further
     * analysis (checked with the database).
     */
    public void push_char(int c, long duration) {
        Toast.makeText(context, String.valueOf(c), Toast.LENGTH_SHORT);
        long latency = 0;
        if (curWord.length() > 0) {
            latency = getLatency(duration);
        }
        if ((char)c == ' ') {
            // check if the word is in the database and integrate the timings
            updateEntry();
            // reset the word and the timings
            resetWord();
        } else if (c == Keyboard.KEYCODE_DELETE) {
            Toast.makeText(context, "pressed delete", Toast.LENGTH_SHORT);
            resetWord();
        } else if ((c > 64 && c < 91) || (c > 96 && c < 123)) { // A-Z and a-z
            if (curWord.length() > 0) {
                curTimings.add(latency);
            }
            curTimings.add(duration);
            curWord = curWord.concat(String.valueOf((char)c));
        }
    }

    /* subtracts the duration from the time between newTimer and timer to get the latency, also
     * updates the timer.
     */
    private long getLatency(long duration) {
        long newTimer = elapsedRealtime();
        long latency = newTimer - timer - duration;
        if (latency < 0) {
            latency = 0;
        }
        timer = newTimer;
        return latency;
    }

    /* resets the word and timings */
    private void resetWord() {
        curWord = "";
        curTimings.clear();
    }

    /* returns the updated mean from the given parameters */
    private double updateMean(double curMean, double value, int curCount) {
        return curMean + (value - curMean)/(curCount + 1);
    }

    /* returns the updated variance from the given parameters */
    private double updateVar(double curVar, double curMean, double value, int curCount) {
        return ((double)curCount - 1)/(double)curCount*curVar + (value - curMean)*(value - curMean)/(curCount + 1);
    }

    /* Checks if the timings for this word are good or not. It goes through each timing and figures
     * out whether it falls in the threshold or not. It keeps track of how many timings are good and
     * checks for the overall goodness. If the timings are bad, then the score is increases
     * proportional to the square root of the length of the word. Once the score is high enough,
     * some sort of alert will be given to user. This function returns a boolean which indicates
     * whether the timing was good or not.
     */
    private boolean checkWordUpdateScore(ArrayList<Double> timings) {
        boolean result;
        int length = curWord.length();
        int miniScore = 0;
        for (int i = 0; i < 4*length - 2; i += 2) {
            long value = curTimings.get(i>>1);
            double avg = timings.get(i);
            double stdDev = Math.sqrt(timings.get(i + 1));
            if (abs(value - avg) < STDDEV_THRESHOLD*stdDev) {
                miniScore++;
            }
        }
        int num, dom;
        if (length == 1) {
            num = 1;
            dom = 1;
        } else if (length == 2) {
            num = 2;
            dom = 3;
        } else {
            num = 3;
            dom = 4;
        }
        if (dom*miniScore < num*(2*length - 1)) {
            score += Math.sqrt(length);
            result = false;
        } else {
            score -= Math.sqrt(length);
            if (score < 0) {
                score = 0;
            }
            result = true;
        }
        inspectScore();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(scoreID, String.valueOf(score));
        editor.commit();
        return result;
    }


    /* Looks at the score to see if it is above the threshold, yes: do some action and reset score
     * no: do nothing.
     */
    private void inspectScore() {
        //Log.i("score", String.valueOf(score));
        Toast.makeText(context, String.valueOf(score), Toast.LENGTH_SHORT).show();
        if (score > SCORE_THRESHOLD) {
            Toast.makeText(context, String.valueOf(score) + " is over " + SCORE_THRESHOLD, Toast.LENGTH_SHORT).show();
            score = 0;
            exitToHome();
        }
    }

    private void exitToHome() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(startMain);
    }

    /* Called when there is a viable word to investigate. The curWord is first checked against the
     * hash map of all the common words. If that condition passes, then the word is searched for in
     * the database. If the word is in the database, then a couple of things happen depending on a
     * some conditions:
     *
     *    1.) if the amount of times the word has been encountered is less than a certain amount,
     *        the timings get integrated with the current timing stats (average and variance) in the
     *        database. The first few set of words is crucial for determining the average and
     *        variance for the timings.
     *    2.) if amount is higher, then it gets tested for whether the timings are good or not using
     *        checkWordUpdateScore() method. If the timings are not good, then this function doesn't
     *        do anything (but checkWordUpdateScore() will update the score). If the timiings are
     *        good, then the database is updated with these timings.
     *
     *  If the word is not in the database, then it is inserted with the appropriate initial values
     *  for each average and variance.
     */
    private void updateEntry() {
        // check if word is in the word set
        String word = curWord.toLowerCase();
        if (wordSet.contains(word)) {
            SQLiteDatabase dbRead = dbHelper.getReadableDatabase();
            SQLiteDatabase dbWRite = dbHelper.getWritableDatabase();

            // search for word in database
            Cursor cursor = searchWord(word, dbRead);

            // check if the query returned anything, yes: update, no: insert row
            int length = word.length();
            if (cursor.getCount() > 0) {
                int count = extractCount(cursor);
                ArrayList<Double> timings = extractTimings(cursor, length);
                if (count < COUNT_THRESHOLD || checkWordUpdateScore(timings)) {
                    updateWord(dbRead, cursor, length, timings, count);
                }
            } else {
                insertWord(word, dbWRite, length);
            }
        }
    }

    private Cursor searchWord(String word, SQLiteDatabase dbRead) {
        String selection = TimeProfileContract.TimeProfile.C_WORD + " = ?";
        String[] selectionArgs = {word};
        return dbRead.query(TimeProfileContract.TimeProfile.TABLE_NAME, null, selection, selectionArgs, null, null, null);
    }

    /* gets the amount of times the word has been encountered from the database */
    private int extractCount(Cursor cursor) {
        cursor.moveToFirst();
        return cursor.getInt(cursor.getColumnIndexOrThrow(TimeProfileContract.TimeProfile.C_COUNT));
    }

    /* get the timing info for the word and returns in in a ArrayList */
    @NonNull
    private ArrayList<Double> extractTimings(Cursor cursor, int length) {
        cursor.moveToFirst();
        ArrayList<Double> timings = new ArrayList<>();
        for (int i = 0; i < length - 1; ++i) {
            timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("d" + String.valueOf(i) + "_avg")));
            timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("d" + String.valueOf(i) + "_var")));
            timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("l" + String.valueOf(i) + "_avg")));
            timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("l" + String.valueOf(i) + "_var")));
        }
        timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("d" + String.valueOf(length - 1) + "_avg")));
        timings.add(cursor.getDouble(cursor.getColumnIndexOrThrow("d" + String.valueOf(length - 1) + "_var")));
        return timings;
    }

    /* updates the database with the curTimings */
    private void updateWord(SQLiteDatabase dbRead, Cursor cursor, int length, ArrayList<Double> timings, int count) {
        ContentValues row = new ContentValues();
        for (int i = 0; i < 4*length - 2; i += 2) {
            double avg = timings.get(i);
            double var = timings.get(i + 1);
            long value = curTimings.get(i>>1);
            double newAvg = updateMean(avg, value, count);
            double newVar = updateVar(var, avg, value, count);
            if ((i>>1)%2 == 0) { // i/2 is even
                row.put("d" + String.valueOf(i>>2) + "_avg", newAvg);
                row.put("d" + String.valueOf(i>>2) + "_var", newVar);
            } else {
                row.put("l" + String.valueOf(i>>2) + "_avg", newAvg);
                row.put("l" + String.valueOf(i>>2) + "_var", newVar);
            }
        }
        row.put(TimeProfileContract.TimeProfile.C_COUNT, ++count);
        dbRead.update(TimeProfileContract.TimeProfile.TABLE_NAME, row, "_id=" + cursor.getLong(cursor.getColumnIndex(TimeProfileContract.TimeProfile._ID)), null);
    }

    /* inserts the word into the database */
    private void insertWord(String word, SQLiteDatabase dbWRite, int length) {
        ContentValues row = new ContentValues();
        row.put(TimeProfileContract.TimeProfile.C_WORD, word);
        for (int i = 0; i < 4*length - 2; i += 2) {
            if ((i>>1)%2 == 0) { // i/2 is even
                row.put("d" + String.valueOf(i>>2) + "_avg", curTimings.get(i>>1));
                row.put("d" + String.valueOf(i>>2) + "_var", 0);
            } else {
                row.put("l" + String.valueOf(i>>2) + "_avg", curTimings.get(i>>1));
                row.put("l" + String.valueOf(i>>2) + "_var", 0);
            }
        }
        row.put(TimeProfileContract.TimeProfile.C_COUNT, 1);
        dbWRite.insert(TimeProfileContract.TimeProfile.TABLE_NAME, null, row);
    }

}
