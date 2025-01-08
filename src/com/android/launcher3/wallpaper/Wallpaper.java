package com.android.launcher3.wallpaper;

public class Wallpaper {

    private long id;
    private String imagePath;
    private int rank;
    private long timestamp;

    public Wallpaper(long id, String imagePath, int rank, long timestamp) {
        this.id = id;
        this.imagePath = imagePath;
        this.rank = rank;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Wallpaper wallpaper = (Wallpaper) obj;
        return id == wallpaper.id && 
               imagePath != null && imagePath.equals(wallpaper.imagePath);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(id);
        result = 31 * result + (imagePath != null ? imagePath.hashCode() : 0);
        return result;
    }
}
